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

package org.springframework.session.data.neo4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.neo4j.ogm.model.Property;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.response.model.NodeModel;
import org.neo4j.ogm.session.SessionFactory;
import org.neo4j.ogm.transaction.Transaction;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

// TODO: Update JavaDoc
/**
 * A {@link org.springframework.session.SessionRepository} implementation that uses
 * Spring's {@link JdbcOperations} to store sessions in a relational database. This
 * implementation does not support publishing of session events.
 * 
 * @author Vedran Pavic
 * @author Eric Spiegelberg
 */
public class OgmSessionRepository implements
		FindByIndexNameSessionRepository<OgmSessionRepository.OgmSession> {

	public static final String NOW = "now";
	public static final String SESSION_ID = "sessionId";	
	public static final String CREATION_TIME = "creationTime";
	public static final String PRINCIPAL_NAME = "principalName";
	public static final String NODE_PROPERTEIS = "nodeProperties";
	public static final String ATTRIBUTE_KEY_PREFIX = "attribute_";
	public static final String LAST_ACCESS_TIME = "lastAccessedTime";
	public static final String MAX_INACTIVE_INTERVAL = "maxInactiveInterval";
	
	/**
	 * The default node label used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_LABEL = "SPRING_SESSION";
	
	public static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	public static final String CREATE_SESSION_QUERY = "create (n:%LABEL% {nodeProperties})";

	public static final String GET_SESSION_QUERY = "match (n:%LABEL%) where n.sessionId={sessionId} return n order by n.creationTime desc";
	
	public static final String UPDATE_SESSION_QUERY = "match (n:%LABEL%) where n.sessionId={sessionId} set %PROPERTIES_TO_UPDATE%";
	
	public static final String DELETE_SESSION_QUERY = "match (n:%LABEL%) where n.sessionId={sessionId} detach delete n";
	
	public static final String LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY = "match (n:%LABEL%) where n.principalName={principalName} return n order by n.creationTime desc";

	public static final String DELETE_SESSIONS_BY_LAST_ACCESS_TIME_QUERY = 
			"match (n:%LABEL%) where n.maxInactiveInterval < ({now} - n.lastAccessedTime) detach delete n";

	private static final Log logger = LogFactory.getLog(OgmSessionRepository.class);
	
	private static final PrincipalNameResolver PRINCIPAL_NAME_RESOLVER = new PrincipalNameResolver();

	private final SessionFactory sessionFactory;
	
	/**
	 * The name of label used by Spring Session to store sessions.
	 */
	private String label = DEFAULT_LABEL;
	
	private String createSessionQuery;
	
	private String getSessionQuery;
	
	private String updateSessionQuery;
	
	private String deleteSessionQuery;
	
	private String listSessionsByPrincipalNameQuery;

	private String deleteSessionsByLastAccessTimeQuery;
	
	/**
	 * If non-null, this value is used to override the default value for
	 * {@link OgmSession#setMaxInactiveInterval(Duration)}.
	 */
	private Integer defaultMaxInactiveInterval;

	private ConversionService conversionService;

	/**
	 * Create a new {@link OgmSessionRepository} instance which uses the
	 * provided {@link JdbcOperations} to manage sessions.
	 * @param jdbcOperations the {@link JdbcOperations} to use
	 * @param transactionManager the {@link PlatformTransactionManager} to use
	 */
	public OgmSessionRepository(SessionFactory sessionFactory) {
		Assert.notNull(sessionFactory, "Property 'sessionFactory' must not be null");
		this.sessionFactory = sessionFactory;
		this.conversionService = createDefaultConversionService();
		prepareQueries();
	}
	
	/**
	 * Set the label used to store sessions.
	 * @param label the label
	 */
	public void setLabel(String label) {
		Assert.hasText(label, "Label must not be empty");
		this.label = label.trim();
		prepareQueries();
	}

	/**
	 * Set the custom Cypher query used to create the session.
	 * @param createSessionQuery the Cypher query string
	 */
	public void setCreateSessionQuery(String createSessionQuery) {
		Assert.hasText(createSessionQuery, "createSessionQuery must not be empty");
		this.createSessionQuery = createSessionQuery;
	}
	
	/**
	 * Set the custom Cypher query used to retrieve the session.
	 * @param getSessionQuery the Cypher query string
	 */
	public void setGetSessionQuery(String getSessionQuery) {
		Assert.hasText(getSessionQuery, "getSessionQuery must not be empty");
		this.getSessionQuery = getSessionQuery;
	}
	
	/**
	 * Set the custom Cypher query used to update the session.
	 * @param updateSessionQuery the Cypher query string
	 */
	public void setUpdateSessionQuery(String updateSessionQuery) {
		Assert.hasText(updateSessionQuery, "updateSessionQuery must not be empty");
		this.updateSessionQuery = updateSessionQuery;
	}
	
	/**
	 * Set the custom Cypher query used to delete the session.
	 * @param deleteSessionQuery the Cypher query string
	 */
	public void setDeleteSessionQuery(String deleteSessionQuery) {
		Assert.hasText(deleteSessionQuery, "deleteSessionQuery must not be empty");
		this.deleteSessionQuery = deleteSessionQuery;
	}
	
	/**
	 * Set the custom Cypher query used to retrieve the sessions by principal name.
	 * @param listSessionsByPrincipalNameQuery the Cypher query string
	 */
	public void setListSessionsByPrincipalNameQuery(String listSessionsByPrincipalNameQuery) {
		Assert.hasText(listSessionsByPrincipalNameQuery, "Query must not be empty");
		this.listSessionsByPrincipalNameQuery = listSessionsByPrincipalNameQuery;
	}

	/**
	 * Set the custom Cypher query used to delete the sessions by last access time.
	 * @param deleteSessionsByLastAccessTimeQuery the Cypher query string
	 */
	public void setDeleteSessionsByLastAccessTimeQuery(String deleteSessionsByLastAccessTimeQuery) {
		Assert.hasText(deleteSessionsByLastAccessTimeQuery, "Query must not be empty");
		this.deleteSessionsByLastAccessTimeQuery = deleteSessionsByLastAccessTimeQuery;
	}
	
	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * timeout. The default is 1800 (30 minutes).
	 * @param defaultMaxInactiveInterval the maximum inactive interval in seconds
	 */
	public void setDefaultMaxInactiveInterval(Integer defaultMaxInactiveInterval) {
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Sets the {@link ConversionService} to use.
	 * @param conversionService the converter to set
	 */
	public void setConversionService(ConversionService conversionService) {
		Assert.notNull(conversionService, "conversionService must not be null");
		this.conversionService = conversionService;
	}

	public OgmSession createSession() {
		OgmSession session = new OgmSession();
		if (this.defaultMaxInactiveInterval != null) {
			session.setMaxInactiveInterval(Duration.ofSeconds(this.defaultMaxInactiveInterval));
		}
		return session;
	}
	
	public void save(final OgmSession session) {
		if (session.isNew()) {

			Map<String, Object> parameters = new HashMap<>(1);
			
			int size = session.getAttributeNames().size() + 5;			
			Map<String, Object> nodeProperties = new HashMap<>(size);			
			parameters.put(NODE_PROPERTEIS, nodeProperties);

			nodeProperties.put(SESSION_ID, session.getId());
			nodeProperties.put(CREATION_TIME, session.getCreationTime().toEpochMilli());
			nodeProperties.put(PRINCIPAL_NAME, session.getPrincipalName());
			nodeProperties.put(LAST_ACCESS_TIME, session.getLastAccessedTime().toEpochMilli());
			nodeProperties.put(MAX_INACTIVE_INTERVAL, session.getMaxInactiveInterval().toMillis());
			
			for (String attributeName : session.getAttributeNames()) {
				
				Optional<Object> attributeValue = session.getAttribute(attributeName);

				if (attributeValue.isPresent()) {
					// TODO performance: Serialize the attributeValue only if it is not a native Neo4j type
					String key = ATTRIBUTE_KEY_PREFIX + attributeName;
					Object value = attributeValue.get();
					byte attributeValueAsBytes[] = serialize(value);
					nodeProperties.put(key, attributeValueAsBytes);
				}

			}

			executeCypher(createSessionQuery, parameters);
			
		} else {

			Map<String, Object> delta = session.getDelta();
			
			Map<String, Object> parameters = new HashMap<>(4 + delta.size());
			parameters.put(SESSION_ID, session.getId());			
			parameters.put(PRINCIPAL_NAME, session.getPrincipalName());
			parameters.put(LAST_ACCESS_TIME, session.getLastAccessedTime().toEpochMilli());
			parameters.put(MAX_INACTIVE_INTERVAL, session.getMaxInactiveInterval().toMillis());
	
			if (!delta.isEmpty()) {		
				for (final Map.Entry<String, Object> entry : delta.entrySet()) {
// TODO performance: Serialize the attributeValue only if it is not a native Neo4j type
					String key = ATTRIBUTE_KEY_PREFIX + entry.getKey();
					Object value = entry.getValue();
					byte attributeValueAsBytes[] = serialize(value);
					parameters.put(key, attributeValueAsBytes);
				}

			}

			StringBuilder stringBuilder = new StringBuilder();			
			Iterator<Entry<String, Object>> entries = parameters.entrySet().iterator();
			while (entries.hasNext()) {
				Entry<String, Object> entry = entries.next();
				String key = entry.getKey();
				Object value = entry.getValue();

				if (value != null) {
					stringBuilder.append("n.");
					stringBuilder.append(key);
					stringBuilder.append("={");					
					stringBuilder.append(key);
					stringBuilder.append("}");
					if (entries.hasNext()) {
						stringBuilder.append(",");
					}
				}
			}

			String suffix = stringBuilder.toString();

			String updateSessionCypher = updateSessionQuery.replace("%PROPERTIES_TO_UPDATE%", suffix);

			executeCypher(updateSessionCypher, parameters);
		}

		session.clearChangeFlags();
	}

	@Override
	public OgmSession getSession(final String sessionId) {

		OgmSession ogmSession = null;
				
		Map<String, Object> parameters = new HashMap<>(1);
		parameters.put(SESSION_ID, sessionId);
		
		Result result = executeCypher(getSessionQuery, parameters);
		
		Iterator<Map<String, Object>> resultIterator = result.iterator();
		
		if (resultIterator.hasNext()) {
		
			Map<String, Object> r = resultIterator.next();			
			NodeModel nodeModel = (NodeModel) r.get("n");
		
			MapSession session = new MapSession(sessionId);
			
			long creationTime = (long) nodeModel.property(CREATION_TIME);			
			session.setCreationTime(Instant.ofEpochMilli(creationTime));

			long lastAccessedTime = ((Number) nodeModel.property(LAST_ACCESS_TIME)).longValue();
			session.setLastAccessedTime(Instant.ofEpochMilli(lastAccessedTime));
			
			long maxInactiveInterval = ((Number) nodeModel.property(MAX_INACTIVE_INTERVAL)).longValue();
			session.setMaxInactiveInterval(Duration.ofMillis(maxInactiveInterval));

			boolean expired = session.isExpired();
			
			if (expired) {
				delete(sessionId);
			} else {
				
				List<Property<String, Object>> propertyList = nodeModel.getPropertyList();			
				for (Property<String, Object> property : propertyList) {
					String attributeName = property.getKey();
					if (attributeName.startsWith(ATTRIBUTE_KEY_PREFIX)) { // Strip the ATTRIBUTE_KEY_PREFIX
						attributeName = attributeName.substring(10);
						byte bytes[] = (byte[]) property.getValue();
						Object attributeValue = deserialize(bytes);
						session.setAttribute(attributeName, attributeValue);
					}				
				}
			
				ogmSession = new OgmSession(session);
				
			}
			
		}
		
		return ogmSession;
	}

	@Override
	public void delete(String sessionId) {
		Map<String, Object> parameters = new HashMap<>(1);
		parameters.put(SESSION_ID, sessionId);		
		executeCypher(this.deleteSessionQuery, parameters);
	}
	
	public Map<String, OgmSession> findByIndexNameAndIndexValue(String indexName,
			final String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}

		Map<String, Object> parameters = new HashMap<String, Object>(1);
		parameters.put(PRINCIPAL_NAME, indexValue);
		Result result = executeCypher(listSessionsByPrincipalNameQuery, parameters);
		
		Map<String, OgmSession> sessionMap = new HashMap<>();
	
		Iterator<Map<String, Object>> resultIterator = result.iterator();
		
		// TODO: DRY with getSession()
		if (resultIterator.hasNext()) {
			
			Map<String, Object> r = resultIterator.next();			
			NodeModel nodeModel = (NodeModel) r.get("n");

			String sessionId = (String) nodeModel.property(SESSION_ID);
			MapSession session = new MapSession(sessionId);
			
			// TODO: Decide what to do with the principal name
			String principalName = (String) nodeModel.property(PRINCIPAL_NAME);
			//session.setPrincipalName(principalName);
			
			long creationTime = (long) nodeModel.property(CREATION_TIME);			
			session.setCreationTime(Instant.ofEpochMilli(creationTime));
			
			long lastAccessedTime = (long) nodeModel.property(LAST_ACCESS_TIME);
			session.setLastAccessedTime(Instant.ofEpochMilli(lastAccessedTime));
			
			long maxInactiveInterval = (long) nodeModel.property(MAX_INACTIVE_INTERVAL);
			session.setMaxInactiveInterval(Duration.ofMillis(maxInactiveInterval));
			
			List<Property<String, Object>> propertyList = nodeModel.getPropertyList();
			for (Property<String, Object> property : propertyList) {
				String attributeName = property.getKey();
				if (attributeName.startsWith(ATTRIBUTE_KEY_PREFIX)) {
					attributeName = attributeName.substring(10); // Strip the ATTRIBUTE_KEY_PREFIX
					byte bytes[] = (byte[]) property.getValue();
					Object attributeValue = deserialize(bytes);
					session.setAttribute(attributeName, attributeValue);
				}				
			}
			
			OgmSession ogmSession = new OgmSession(session);
			sessionMap.put(sessionId, ogmSession);
			
		}

		return sessionMap;
	}

	@Scheduled(cron = "${spring.session.cleanup.cron.expression:0 * * * * *}")
	public void cleanUpExpiredSessions() {

		Date now = new Date();
		Map<String, Object> parameters = new HashMap<>(1);
		parameters.put(NOW, now.getTime());
		Result result = executeCypher(deleteSessionsByLastAccessTimeQuery, parameters);
		
		if (logger.isDebugEnabled()) {
			int deletedCount = result.queryStatistics().getNodesDeleted();
			logger.debug("Cleaned up " + deletedCount + " expired sessions");
		}
	}

	private static GenericConversionService createDefaultConversionService() {
		GenericConversionService converter = new GenericConversionService();
		converter.addConverter(Object.class, byte[].class,
				new SerializingConverter());
		converter.addConverter(byte[].class, Object.class,
				new DeserializingConverter());
		return converter;
	}

	private String getQuery(String base) {
		return StringUtils.replace(base, "%LABEL%", this.label);
	}
	
	private void prepareQueries() {
		this.createSessionQuery = getQuery(CREATE_SESSION_QUERY);
		this.getSessionQuery = getQuery(GET_SESSION_QUERY);
		this.updateSessionQuery = getQuery(UPDATE_SESSION_QUERY);
		this.deleteSessionQuery = getQuery(DELETE_SESSION_QUERY);
		this.listSessionsByPrincipalNameQuery =
				getQuery(LIST_SESSIONS_BY_PRINCIPAL_NAME_QUERY);		
		this.deleteSessionsByLastAccessTimeQuery =
				getQuery(DELETE_SESSIONS_BY_LAST_ACCESS_TIME_QUERY);
	}
	
	public byte[] serialize(Object attributeValue) {		
		byte bytes[] = (byte[]) this.conversionService.convert(attributeValue,
				TypeDescriptor.valueOf(Object.class),
				TypeDescriptor.valueOf(byte[].class));

		return bytes;
	}

	public Object deserialize(Object attributeValue) {
		Object o = this.conversionService.convert(attributeValue, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
		return o;		
	}

	/**
	 * The {@link Session} to use for {@link OgmSessionRepository}.
	 */
	final class OgmSession implements Session {

		private final Session delegate;

		private boolean isNew;

		private boolean changed;

		private Map<String, Object> delta = new HashMap<>();

		OgmSession() {
			this.delegate = new MapSession();
			this.isNew = true;
		}

		OgmSession(Session delegate) {
			Assert.notNull(delegate, "Session cannot be null");
			this.delegate = delegate;
		}

		boolean isNew() {
			return this.isNew;
		}

		boolean isChanged() {
			return this.changed;
		}

		Map<String, Object> getDelta() {
			return this.delta;
		}

		void clearChangeFlags() {
			this.isNew = false;
			this.changed = false;
			this.delta.clear();
		}

		String getPrincipalName() {
			return PRINCIPAL_NAME_RESOLVER.resolvePrincipal(this);
		}

		public String getId() {
			return this.delegate.getId();
		}

		public <T> Optional<T> getAttribute(String attributeName) {
			return this.delegate.getAttribute(attributeName);
		}

		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		public void setAttribute(String attributeName, Object attributeValue) {
			this.delegate.setAttribute(attributeName, attributeValue);
			this.delta.put(attributeName, attributeValue);
			if (PRINCIPAL_NAME_INDEX_NAME.equals(attributeName) ||
					SPRING_SECURITY_CONTEXT.equals(attributeName)) {
				this.changed = true;
			}
		}

		public void removeAttribute(String attributeName) {
			this.delegate.removeAttribute(attributeName);
			this.delta.put(attributeName, null);
		}

		public Instant getCreationTime() {
			return this.delegate.getCreationTime();
		}

		public void setLastAccessedTime(Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.changed = true;
		}

		public Instant getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		public void setMaxInactiveInterval(Duration interval) {
			this.delegate.setMaxInactiveInterval(interval);
			this.changed = true;
		}

		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

		public boolean isExpired() {
			return this.delegate.isExpired();
		}

	}

	/**
	 * Resolves the Spring Security principal name.
	 *
	 * @author Vedran Pavic
	 */
	static class PrincipalNameResolver {

		private SpelExpressionParser parser = new SpelExpressionParser();

		public String resolvePrincipal(Session session) {
			Optional<String> principalName = session.getAttribute(PRINCIPAL_NAME_INDEX_NAME);
			if (principalName.isPresent()) {
				return principalName.get();
			}
			Optional<Object> authentication = session.getAttribute(SPRING_SECURITY_CONTEXT);
			if (authentication.isPresent()) {
				Expression expression = this.parser
						.parseExpression("authentication?.name");
				return expression.getValue(authentication.get(), String.class);
			}
			return null;
		}

	}

	protected Result executeCypher(String cypher, Map<String, Object> parameters) {

		org.neo4j.ogm.session.Session ogmSession = sessionFactory.openSession();

		Transaction transaction = ogmSession.beginTransaction();

		try {

			Result result = ogmSession.query(cypher, parameters);

			transaction.commit();

			return result;
			
		} catch (Exception e) {
			String message = "Exception while executing cypher: '" + cypher + "'";
			logger.error(message);
			transaction.rollback();
			throw new RuntimeException(message, e);
		} finally {
			transaction.close();
		}
		
	}
	
	/**
	 * Neo4j natively supports values of either Java primitive types (float, double, int, boolean, byte,... ), Strings or an array of both.
	 * 
	 * @param o The object to evaluate.
	 * @return boolean true if the object is a Neo4j supported data type otherwise false.
	 */
	protected boolean isNeo4jSupportedType(Object o) {
	
		Class<?> clazz = o.getClass();
		boolean supported = ClassUtils.isPrimitiveOrWrapper(clazz);
		
		if (!supported) {
			supported = ClassUtils.isPrimitiveWrapperArray(clazz);	
		}

		if (!supported) {
			supported = o instanceof byte[];	
		}
		
		if (!supported) {
			supported = o instanceof String;	
		}
		
		if (!supported) {
			supported = o instanceof String[];	
		}
		
		return supported;
		
	}
	
}
