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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.util.ContentTypeUtils;
import org.thymeleaf.util.StringUtils;
import org.thymeleaf.util.Validate;

/**
 * @author OGAWA, Takeshi
 */
public class WallRideResourceThemeTemplateResolver extends AbstractConfigurableTemplateResolver implements ApplicationContextAware {

	static final String DEFAULT_THEME = "default";
	static final String THEMES_PATH = "themes/";
	static final String TEMPLATES_FOLDER = "/templates/";

	private static Logger logger = LoggerFactory.getLogger(WallRideResourceThemeTemplateResolver.class);
	
	private ApplicationContext applicationContext = null;

	public WallRideResourceThemeTemplateResolver() {
		setCheckExistence(true);
	}

	public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
	
	@Override
	protected String computeResourceName(IEngineConfiguration configuration, String ownerTemplate, String template,
			String prefix, String suffix, boolean forceSuffix, Map<String, String> templateAliases,
			Map<String, Object> templateResolutionAttributes) {
		
		logger.debug("computeResourceName ownerTemplate: " + ownerTemplate + " template: " + template + " prefix: " + prefix + " templateResolutionAttributes:" + templateResolutionAttributes);
		
		Validate.notNull(template, "Template name cannot be null");

        String unaliasedName = templateAliases.get(template);
        if (unaliasedName == null) {
            unaliasedName = template;
        }
        
        String themePrefix = DEFAULT_THEME;
		if(templateResolutionAttributes!=null) {
			themePrefix = (String)templateResolutionAttributes.getOrDefault("theme", DEFAULT_THEME);
		}		
		unaliasedName = themePrefix + TEMPLATES_FOLDER + unaliasedName;

        final boolean hasPrefix = !StringUtils.isEmptyOrWhitespace(prefix);
        final boolean hasSuffix = !StringUtils.isEmptyOrWhitespace(suffix);

        final boolean shouldApplySuffix =
                hasSuffix && (forceSuffix || !ContentTypeUtils.hasRecognizedFileExtension(unaliasedName));

        if (!hasPrefix && !shouldApplySuffix){
            return unaliasedName;
        }

        if (!hasPrefix) { // shouldApplySuffix
            return unaliasedName + suffix;
        }

        if (!shouldApplySuffix) { // hasPrefix
            return prefix + unaliasedName;
        }

        // hasPrefix && shouldApplySuffix
        return prefix + unaliasedName + suffix;
	}

	@Override
	protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, String resourceName, String characterEncoding, Map<String, Object> templateResolutionAttributes) {
		
		logger.debug("DEFAULT computeTemplateResource ownerTemplate: " + ownerTemplate + " template: " + template + " resourceName: " + resourceName + " templateResolutionAttributes:" + templateResolutionAttributes);
		
		return new WallRideResourceTemplateResource(this.applicationContext, resourceName, characterEncoding);
	}
}
