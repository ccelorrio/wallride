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

package org.wallride.job;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.wallride.domain.Post;
import org.wallride.exception.ServiceException;
import org.wallride.repository.PostRepository;
import org.wallride.service.BlogService;
import org.wallride.web.controller.guest.article.ArticleDescribeController;
import org.wallride.web.controller.guest.page.PageDescribeController;
import org.wallride.web.support.BlogLanguageRewriteMatch;
import org.wallride.web.support.BlogLanguageRewriteRule;

import com.google.api.services.analyticsreporting.v4.model.ReportRow;

@Component
@StepScope
public class UpdatePostViewsItemWriter implements ItemWriter<ReportRow> {

	@Inject
	private ServletContext servletContext;

	@Autowired
	private BlogService blogService;

	@Autowired
	private PostRepository postRepository;

	private static Logger logger = LoggerFactory.getLogger(UpdatePostViewsItemWriter.class);

	@Override
	public void write(List<? extends ReportRow> items) throws Exception {
		WebApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(servletContext, "org.springframework.web.servlet.FrameworkServlet.CONTEXT.guestServlet");
		if (context == null) {
			throw new ServiceException("GuestServlet is not ready yet");
		}

		final RequestMappingHandlerMapping mapping = context.getBean(RequestMappingHandlerMapping.class);

		for (ReportRow item : items) {
			UriComponents uriComponents = UriComponentsBuilder.fromUriString((String) item.getDimensions().get(0)).build();
			logger.info("Processing [{}]", uriComponents.toString());

			MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
			request.setMethod("GET");
			request.setRequestURI(uriComponents.getPath());
			request.setQueryString(uriComponents.getQuery());
			MockHttpServletResponse response = new MockHttpServletResponse();

			BlogLanguageRewriteRule rewriteRule = new BlogLanguageRewriteRule(blogService);
			BlogLanguageRewriteMatch rewriteMatch = (BlogLanguageRewriteMatch) rewriteRule.matches(request, response);
			try {
				rewriteMatch.execute(request, response);
			} catch (ServletException e) {
				throw new ServiceException(e);
			} catch (IOException e) {
				throw new ServiceException(e);
			}

			request.setRequestURI(rewriteMatch.getMatchingUrl());

			HandlerExecutionChain handler;
			try {
				handler = mapping.getHandler(request);
			} catch (Exception e) {
				throw new ServiceException(e);
			}

			if (!(handler.getHandler() instanceof HandlerMethod)) {
				continue;
			}

			HandlerMethod method = (HandlerMethod) handler.getHandler();
			logger.debug("Controller used [{}]", method.getBeanType());
			if (!method.getBeanType().equals(ArticleDescribeController.class) && !method.getBeanType().equals(PageDescribeController.class)) {
				continue;
			}

			// Last path mean code of post
			String code = uriComponents.getPathSegments().get(uriComponents.getPathSegments().size() - 1);
			Post post = postRepository.findOneByCodeAndLanguage(code, rewriteMatch.getBlogLanguage().getLanguage());
			if (post == null) {
				logger.debug("Post not found [{}]", code);
				continue;
			}

			Long itemViews = Long.parseLong((String) item.getMetrics().get(0).getValues().get(0));
			logger.info("Update the PageView. Post ID [{}]: {} -> {}", post.getId(), post.getViews(), itemViews);
			post.setViews(itemViews);
			postRepository.saveAndFlush(post);
		}
	}
}
