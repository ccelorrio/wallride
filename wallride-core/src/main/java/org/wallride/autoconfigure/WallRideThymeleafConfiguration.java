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

package org.wallride.autoconfigure;

import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.IThrottledTemplateProcessor;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;
import org.thymeleaf.extras.springsecurity5.dialect.SpringSecurityDialect;
import org.thymeleaf.spring5.ISpringTemplateEngine;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.spring5.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.wallride.service.ArticleService;
import org.wallride.service.CategoryService;
import org.wallride.service.PageService;
import org.wallride.service.TagService;
import org.wallride.support.ArticleUtils;
import org.wallride.support.CategoryUtils;
import org.wallride.support.PageUtils;
import org.wallride.support.PostUtils;
import org.wallride.support.TagUtils;
import org.wallride.web.support.ExtendedThymeleafViewResolver;

@Configuration
public class WallRideThymeleafConfiguration {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private WallRideProperties wallRideProperties;

	@Autowired
	private ArticleService articleService;

	@Autowired
	private PageService pageService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private TagService tagService;

	@Inject
	private ThymeleafProperties thymeleafProperties;

	@Inject
	private Environment environment;

	@Bean
	public PostUtils postUtils(PageUtils pageUtils) {
		return new PostUtils(pageUtils);
	}

	@Bean
	public ArticleUtils articleUtils() {
		return new ArticleUtils(articleService);
	}

	@Bean
	public PageUtils pageUtils() {
		return new PageUtils(pageService);
	}

	@Bean
	public CategoryUtils categoryUtils() {
		return new CategoryUtils(categoryService);
	}

	@Bean
	public TagUtils tagUtils() {
		return new TagUtils(tagService);
	}

	@Bean
	@ConditionalOnMissingBean
	public WallRideThymeleafDialect wallRideThymeleafDialect(WallRideExpressionObjectFactory expressionObjectFactory) {
		return new WallRideThymeleafDialect(expressionObjectFactory);
	}

	@Bean
	@ConditionalOnMissingBean
	public WallRideExpressionObjectFactory wallRideExpressionObjectFactory() {
		WallRideExpressionObjectFactory expressionObjectFactory = new WallRideExpressionObjectFactory();
		ArticleUtils articleUtils = articleUtils();
		PageUtils pageUtils = pageUtils();
		expressionObjectFactory.setPostUtils(postUtils(pageUtils));
		expressionObjectFactory.setArticleUtils(articleUtils);
		expressionObjectFactory.setPageUtils(pageUtils);
		expressionObjectFactory.setCategoryUtils(categoryUtils());
		expressionObjectFactory.setTagUtils(tagUtils());
		expressionObjectFactory.setWallRideProperties(wallRideProperties);
		return expressionObjectFactory;
	}
	
	@Bean(name = {"defaultTemplateResolver", "homePathTemplateResolver"})
	public ITemplateResolver homePathTemplateResolver() {
		WallRideResourceThemeTemplateResolver resolver = new WallRideResourceThemeTemplateResolver();
//		resolver.setResourceResolver(wallRideResourceResourceResolver);
		resolver.setApplicationContext(applicationContext);
		resolver.setPrefix(wallRideProperties.getHome() + WallRideResourceThemeTemplateResolver.THEMES_PATH);
		System.out.println("WallRideResourceThemeTemplateResolver DEFAULT PATH: " + wallRideProperties.getHome() 
			+ WallRideResourceThemeTemplateResolver.THEMES_PATH + WallRideResourceThemeTemplateResolver.DEFAULT_THEME 
			+ WallRideResourceThemeTemplateResolver.TEMPLATES_FOLDER);
		System.out.println("WallRideResourceThemeTemplateResolver THEME PATH: " + wallRideProperties.getHome() 
			+ WallRideResourceThemeTemplateResolver.THEMES_PATH + "THEME_NAME"
			+ WallRideResourceThemeTemplateResolver.TEMPLATES_FOLDER);
		resolver.setSuffix(this.thymeleafProperties.getSuffix());
		resolver.setTemplateMode(this.thymeleafProperties.getMode());
		resolver.setCharacterEncoding(this.thymeleafProperties.getEncoding().name());
		resolver.setCacheable(this.thymeleafProperties.isCache());
		System.out.println("THEMES Cacheable: " + this.thymeleafProperties.isCache());
		resolver.setOrder(1);
		return resolver;
	}

	@Bean
	public ITemplateResolver classPathTemplateResolver() {
		WallRideResourceTemplateResolver resolver = new WallRideResourceTemplateResolver();
//		resolver.setResourceResolver(wallRideResourceResourceResolver);
		resolver.setApplicationContext(applicationContext);
		resolver.setPrefix(environment.getRequiredProperty("spring.thymeleaf.prefix.guest"));		
		resolver.setSuffix(this.thymeleafProperties.getSuffix());
		resolver.setTemplateMode(this.thymeleafProperties.getMode());
		resolver.setCharacterEncoding(this.thymeleafProperties.getEncoding().name());
		resolver.setCacheable(this.thymeleafProperties.isCache());
		resolver.setOrder(2);
		return resolver;
	}

	@Bean
	public DelegatingTemplateEngine templateEngine(WallRideThymeleafDialect wallRideThymeleafDialect) {
		SpringTemplateEngine engine = new SpringTemplateEngine();
		
//		engine.setTemplateResolver(templateResolver());
		Set<ITemplateResolver> templateResolvers = new LinkedHashSet<>();
		templateResolvers.add(homePathTemplateResolver());
		templateResolvers.add(classPathTemplateResolver());
		engine.setTemplateResolvers(templateResolvers);

		Set<IDialect> dialects = new HashSet<>();
		dialects.add(new SpringSecurityDialect());
		dialects.add(new Java8TimeDialect());
		dialects.add(wallRideThymeleafDialect);
		engine.setAdditionalDialects(dialects);
		return new DelegatingTemplateEngine(engine);
	}

	@Bean
	public ThymeleafViewResolver thymeleafViewResolver(DelegatingTemplateEngine templateEngine) {
		ThymeleafViewResolver viewResolver = new ExtendedThymeleafViewResolver();
		viewResolver.setTemplateEngine(templateEngine);
		viewResolver.setViewNames(this.thymeleafProperties.getViewNames());
		viewResolver.setCharacterEncoding(this.thymeleafProperties.getEncoding().name());
		viewResolver.setContentType(this.thymeleafProperties.getServlet().getContentType() + ";charset=" + this.thymeleafProperties.getEncoding());
		viewResolver.setCache(false);
		viewResolver.setOrder(2);
		return viewResolver;
	}
	
	
	public class DelegatingTemplateEngine implements ISpringTemplateEngine, MessageSourceAware {
		
		private Logger logger = LoggerFactory.getLogger(DelegatingTemplateEngine.class);
		
	    public SpringTemplateEngine delegate;

	    public DelegatingTemplateEngine(SpringTemplateEngine delegate) {
	        this.delegate = delegate;
	    }

	    @Override
	    public void process(String template,
	            Set<String> templateSelectors,
	            IContext context,
	            Writer writer) {
	        Map<String, Object> resolutionAttributes = new HashMap<>();

	        // add your attributes here
	        logger.debug("DelegatingTemplateEngine process " + template + " context: " + context);
	        if(context instanceof IWebContext) {
	        	HttpServletRequest request = ((IWebContext)context).getRequest();
	        	String theme = (String)request.getAttribute("theme");
	        	if(theme!=null && !theme.isEmpty()) {
	        		logger.debug("DelegatingTemplateEngine process ServerName: " + request.getServerName() + " session " + request.getSession().getAttributeNames());
		        	resolutionAttributes.put("theme", theme);
	        	}
	        }

			// create a null `TemplateMode` variable so that it can determine 
			// the correct `TemplateSpec` constructor
	        TemplateMode templateMode = null;
	        final TemplateSpec templateSpec = 
						new TemplateSpec(template, 
										templateSelectors, 
										templateMode, 
										resolutionAttributes);
	        
			delegate.process(templateSpec, context, writer);
	    }

	    
		// Excluded all overrides here, but basically they are just passthroughs 
		// to the delegate's methods.
	    
		@Override
		public IEngineConfiguration getConfiguration() {
			// Just a passthrough
			return delegate.getConfiguration();
		}

		@Override
		public String process(String template, IContext context) {
			// Just a passthrough
			return delegate.process(template, context);
		}

		@Override
		public String process(String template, Set<String> templateSelectors, IContext context) {
			// Just a passthrough
			return delegate.process(template, templateSelectors, context);
		}

		@Override
		public String process(TemplateSpec templateSpec, IContext context) {
			// Just a passthrough
			return delegate.process(templateSpec, context);
		}

		@Override
		public void process(String template, IContext context, Writer writer) {
			// Just a passthrough
			delegate.process(template, context, writer);
		}

		@Override
		public void process(TemplateSpec templateSpec, IContext context, Writer writer) {
			// Just a passthrough
			delegate.process(templateSpec, context, writer);
		}

		@Override
		public IThrottledTemplateProcessor processThrottled(String template, IContext context) {
			// Just a passthrough
			return delegate.processThrottled(template, context);
		}

		@Override
		public IThrottledTemplateProcessor processThrottled(String template, Set<String> templateSelectors,
				IContext context) {
			// Just a passthrough
			return delegate.processThrottled(template, templateSelectors, context);
		}

		@Override
		public IThrottledTemplateProcessor processThrottled(TemplateSpec templateSpec, IContext context) {
			// Just a passthrough
			return delegate.processThrottled(templateSpec, context);
		}

		@Override
		public void setTemplateEngineMessageSource(MessageSource templateEngineMessageSource) {
			// Just a passthrough
			delegate.setTemplateEngineMessageSource(templateEngineMessageSource);
		}


		@Override
		public void setMessageSource(MessageSource messageSource) {
			// Just a passthrough
			delegate.setMessageSource(messageSource);
		}
	}
}
