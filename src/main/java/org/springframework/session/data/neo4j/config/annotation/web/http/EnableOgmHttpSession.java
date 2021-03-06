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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.session.MapSession;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.data.neo4j.OgmSessionRepository;

/**
 * Add this annotation to an {@code @Configuration} class to expose the
 * SessionRepositoryFilter as a bean named "springSessionRepositoryFilter" and backed by a
 * relational database. In order to leverage the annotation, a single
 * {@link org.neo4j.ogm.session.SessionFactory} must be provided. For example:
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;EnableJdbcHttpSession
 * public class OgmHttpSessionConfig {
 *
 *		@Value("${spring.data.neo4j.uri:bolt://neo4j:dev@localhost}")
 *		protected String neo4jUri;
 *
 *		protected Configuration configuration;
 *
 *		&#064;Bean
 *		public Configuration configureOgm() {
 *			Configuration configuration = new Configuration.Builder().uri(neo4jUri).build();
 *			this.configuration = configuration;
 *	    	return configuration;
 *	   }
 *	
 *     &#064;Bean
 *     &#064;Qualifier("springSessionOgmSessionFactory")
 *     public SessionFactory sessionFactory() {
 *        SessionFactory sessionFactory = new SessionFactory(configuration, "com.my.domain");
 *  	  return sessionFactory;
 *     }
 *
 * }
 * </pre>
 *
 * More advanced configurations can extend {@link OgmHttpSessionConfiguration} instead.
 *
 * For additional information on how to configure data access related concerns, please
 * refer to the
 * <a href="http://docs.spring.io/spring/docs/current/spring-framework-reference/html/spring-data-tier.html">
 * Spring Framework Reference Documentation</a>.
 *
 * @see EnableSpringHttpSession
 */
@Documented
@Configuration
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(OgmHttpSessionConfiguration.class)
public @interface EnableOgmHttpSession {

	/**
	 * The name of label used by Spring Session to store sessions.
	 * @return the label name
	 */
	String label() default OgmSessionRepository.DEFAULT_LABEL;

	/**
	 * The session timeout in seconds. By default, it is set to 1800 seconds (30 minutes).
	 * This should be a non-negative integer.
	 *
	 * @return the seconds a session can be inactive before expiring
	 */
	int maxInactiveIntervalInSeconds() default MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

}
