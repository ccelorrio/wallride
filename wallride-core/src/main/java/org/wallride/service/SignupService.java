/*
 * Copyright 2014 Tagbangers, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wallride.service;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.StandardPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.wallride.autoconfigure.WallRideCacheConfiguration;
import org.wallride.domain.Blog;
import org.wallride.domain.User;
import org.wallride.domain.UserInvitation;
import org.wallride.exception.DuplicateEmailException;
import org.wallride.exception.DuplicateLoginIdException;
import org.wallride.exception.ServiceException;
import org.wallride.model.SignupRequest;
import org.wallride.repository.UserInvitationRepository;
import org.wallride.repository.UserRepository;
import org.wallride.support.AuthorizedUser;
import org.wallride.web.support.HttpForbiddenException;

@Service
@Transactional(rollbackFor=Exception.class)
@SuppressWarnings("deprecation") // TODO Use BCryptPasswordEncoder instead of StandardPasswordEncoder
public class SignupService {
	
	@Inject
	private BlogService blogService;

	@Inject
	private JavaMailSender mailSender;

	@Inject
	@Qualifier("emailTemplateEngine")
	private TemplateEngine templateEngine;
	
	@Inject
	private MessageSourceAccessor messageSourceAccessor;
	
	@Inject
	private MailProperties mailProperties;

	@Resource
	private UserRepository userRepository;
	@Resource
	private UserInvitationRepository userInvitationRepository;

	public UserInvitation readUserInvitation(String token) {
		return userInvitationRepository.findOneByToken(token);
	}

	public boolean validateInvitation(UserInvitation invitation) {
		if (invitation == null) {
			return false;
		}
		if (invitation.isAccepted()) {
			return false;
		}
		LocalDateTime now = LocalDateTime.now();
		if (now.isAfter(invitation.getExpiredAt())) {
			return false;
		}
		return true;
	}

	@CacheEvict(value = WallRideCacheConfiguration.USER_CACHE, allEntries = true)
	public void signup(SignupRequest request, User.Role role) throws ServiceException, MessagingException {
		
		String recipient = request.getEmail();

		LocalDateTime now = LocalDateTime.now();

		List<UserInvitation> invitations = new ArrayList<>();

			UserInvitation invitation = new UserInvitation();
			invitation.setEmail(recipient);
			invitation.setMessage("WEB-SIGNUP");
			invitation.setExpiredAt(now.plusHours(72));
			invitation.setCreatedAt(now);
			invitation.setUpdatedAt(now);
			invitation = userInvitationRepository.saveAndFlush(invitation);
			invitations.add(invitation);
		

		Blog blog = blogService.getBlogById(Blog.DEFAULT_ID);

			String websiteTitle = blog.getTitle(LocaleContextHolder.getLocale().getLanguage());
			String signupLink = ServletUriComponentsBuilder.fromCurrentContextPath()
					.path("/signup")
					.queryParam("token", invitation.getToken())
					.buildAndExpand().toString();

			final Context ctx = new Context(LocaleContextHolder.getLocale());
			ctx.setVariable("websiteTitle", websiteTitle);
			ctx.setVariable("signupLink", signupLink);
			ctx.setVariable("invitation", invitation);

			final MimeMessage mimeMessage = mailSender.createMimeMessage();
			final MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "UTF-8"); // true = multipart
			message.setSubject(MessageFormat.format(
					messageSourceAccessor.getMessage("SignUpMessageTitle", LocaleContextHolder.getLocale()),
					websiteTitle));
			message.setFrom(mailProperties.getProperties().get("mail.from"));
			message.setTo(invitation.getEmail());

			final String htmlContent = templateEngine.process("user-invite", ctx);
			message.setText(htmlContent, true); // true = isHtml

			mailSender.send(mimeMessage);
		
	}

	@CacheEvict(value = WallRideCacheConfiguration.USER_CACHE, allEntries = true)
	public AuthorizedUser signup(SignupRequest request, User.Role role, String token) throws ServiceException {
		UserInvitation invitation = null;
		if (token != null) {
			invitation = userInvitationRepository.findOneForUpdateByToken(token);
			if (invitation == null) {
				throw new HttpForbiddenException();
			}
			if (!validateInvitation(invitation)) {
				throw new HttpForbiddenException();
			}
		}

		User duplicate;
		duplicate = userRepository.findOneByLoginId(request.getLoginId());
		if (duplicate != null) {
			throw new DuplicateLoginIdException(request.getLoginId());
		}
		duplicate = userRepository.findOneByEmail(request.getEmail());
		if (duplicate != null) {
			throw new DuplicateEmailException(request.getEmail());
		}

		LocalDateTime now = LocalDateTime.now();
		if (invitation != null) {
			invitation.setAccepted(true);
			invitation.setAcceptedAt(now);
			userInvitationRepository.saveAndFlush(invitation);
		}

		User user = new User();
		user.setLoginId(request.getLoginId());
		StandardPasswordEncoder passwordEncoder = new StandardPasswordEncoder();
		user.setLoginPassword(passwordEncoder.encode(request.getLoginPassword()));
		user.getName().setFirstName(request.getName().getFirstName());
		user.getName().setLastName(request.getName().getLastName());
		user.setEmail(request.getEmail());
		user.getRoles().add(role);
		user.setCreatedAt(now);
		user.setUpdatedAt(now);
		user = userRepository.saveAndFlush(user);

		AuthorizedUser authorizedUser = new AuthorizedUser(user);
//		Authentication auth = new UsernamePasswordAuthenticationToken(authorizedUser, null, authorizedUser.getAuthorities());
//		SecurityContextHolder.getContext().setAuthentication(auth);

		return authorizedUser;
	}
}
