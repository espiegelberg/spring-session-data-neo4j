/*
 * Copyright 2014-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.session.data.neo4j.config.annotation.web.http;

import javax.sql.DataSource;

import org.neo4j.ogm.session.SessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.neo4j.OgmSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

// TODO: Update JavaDoc
/**
 * Spring @Configuration class used to configure and initialize a JDBC based HttpSession
 * provider implementation in Spring Session.
 * <p>
 * Exposes the {@link org.springframework.session.web.http.SessionRepositoryFilter} as a
 * bean named "springSessionRepositoryFilter". In order to use this a single
 * {@link DataSource} must be exposed as a Bean.
 *
 * @author Vedran Pavic
 * @author Eddú Meléndez
 * @since 1.2.0
 * @see EnableJdbcHttpSession
 */
@Configuration
@EnableScheduling
public class OgmHttpSessionConfiguration extends SpringHttpSessionConfiguration {
		//implements BeanClassLoaderAware, ImportAware, EmbeddedValueResolverAware {

	private String label;

	private Integer maxInactiveIntervalInSeconds;

//	@Autowired(required = false)
//	@Qualifier("conversionService")
//	private ConversionService conversionService;
//
//	private ConversionService springSessionConversionService;
//
//	private ClassLoader classLoader;
//
//	private StringValueResolver embeddedValueResolver;

	@Bean
	public OgmSessionRepository sessionRepository(
			@Qualifier("springSessionOgmSessionFactory") SessionFactory sessionFactory) {
		
		OgmSessionRepository sessionRepository = new OgmSessionRepository(sessionFactory);
		
		String label = getLabel();
		if (StringUtils.hasText(label)) {
			sessionRepository.setLabel(label);
		}
		sessionRepository
				.setDefaultMaxInactiveInterval(this.maxInactiveIntervalInSeconds);

// TODO: What is the code below 91 used for? Is it needed?
//		if (this.springSessionConversionService != null) {
//			sessionRepository.setConversionService(this.springSessionConversionService);
//		}
//		else if (this.conversionService != null) {
//			sessionRepository.setConversionService(this.conversionService);
//		}
//		else if (deserializingConverterSupportsCustomClassLoader()) {
//			GenericConversionService conversionService = createConversionServiceWithBeanClassLoader();
//			sessionRepository.setConversionService(conversionService);
//		}
		return sessionRepository;
	}

	/**
	 * This must be a separate method because some ClassLoaders load the entire method
	 * definition even if an if statement guards against it loading. This means that older
	 * versions of Spring would cause a NoSuchMethodError if this were defined in
	 * {@link #sessionRepository(JdbcOperations, PlatformTransactionManager)}.
	 *
	 * @return the default {@link ConversionService}
	 */
//	private GenericConversionService createConversionServiceWithBeanClassLoader() {
//		GenericConversionService conversionService = new GenericConversionService();
//		conversionService.addConverter(Object.class, byte[].class,
//				new SerializingConverter());
//		conversionService.addConverter(byte[].class, Object.class,
//				new DeserializingConverter(this.classLoader));
//		return conversionService;
//	}
//
//	/* (non-Javadoc)
//	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang.ClassLoader)
//	 */
//	public void setBeanClassLoader(ClassLoader classLoader) {
//		this.classLoader = classLoader;
//	}
//
//	@Autowired(required = false)
//	@Qualifier("springSessionConversionService")
//	public void setSpringSessionConversionService(ConversionService conversionService) {
//		this.springSessionConversionService = conversionService;
//	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	private String getLabel() {
		String systemProperty = System.getProperty("spring.session.neo4j.label", "");
		if (StringUtils.hasText(systemProperty)) {
			return systemProperty;
		}
		return this.label;
	}

	private boolean deserializingConverterSupportsCustomClassLoader() {
		return ClassUtils.hasConstructor(DeserializingConverter.class, ClassLoader.class);
	}

//	public void setImportMetadata(AnnotationMetadata importMetadata) {
//		Map<String, Object> enableAttrMap = importMetadata
//				.getAnnotationAttributes(EnableOgmHttpSession.class.getName());
//		AnnotationAttributes enableAttrs = AnnotationAttributes.fromMap(enableAttrMap);
//		String labelValue = enableAttrs.getString("label");
//		if (StringUtils.hasText(labelValue)) {
//			this.label = this.embeddedValueResolver
//					.resolveStringValue(labelValue);
//		}
//		this.maxInactiveIntervalInSeconds = enableAttrs
//				.getNumber("maxInactiveIntervalInSeconds");
//	}
//
//	public void setEmbeddedValueResolver(StringValueResolver resolver) {
//		this.embeddedValueResolver = resolver;
//	}

	/**
	 * Property placeholder to process the @Scheduled annotation.
	 * @return the {@link PropertySourcesPlaceholderConfigurer} to use
	 */
	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}
}
