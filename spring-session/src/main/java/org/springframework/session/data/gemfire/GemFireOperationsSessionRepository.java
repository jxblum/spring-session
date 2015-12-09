/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.gemfire;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.session.ExpiringSession;
import org.springframework.session.Session;
import org.springframework.util.Assert;

import com.gemstone.gemfire.cache.query.SelectResults;
import com.gemstone.gemfire.pdx.PdxReader;
import com.gemstone.gemfire.pdx.PdxSerializable;
import com.gemstone.gemfire.pdx.PdxWriter;

/**
 * The GemFireOperationsSessionRepository class is a Spring SessionRepository implementation that interfaces with
 * and uses GemFire to back and store Spring Sessions.
 *
 * @author John Blum
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @since 1.1.0
 */
@SuppressWarnings("all")
public class GemFireOperationsSessionRepository extends AbstractGemFireOperationsSessionRepository {

	// GemFire OQL query used to look up Sessions by principal name.
	protected static final String FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY =
		"SELECT s FROM %1$s s WHERE s.principalName = $1";

	/**
	 * Constructs an instance of GemFireOperationsSessionRepository initialized with the required GemfireOperations
	 * object used to perform data access operations to manage Session state.
	 *
	 * @param template the GemfireOperations object used to access and manage Session state in GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public GemFireOperationsSessionRepository(GemfireOperations template) {
		super(template);
	}

	/**
	 * Looks up all the available Sessions tied to the specific user identified by principal name.
	 *
	 * @param principalName the principal name (i.e. username) to search for all existing Spring Sessions.
	 * @return a mapping of Session ID to Session instances.
	 * @see org.springframework.session.ExpiringSession
	 */
	public Map<String, ExpiringSession> findByPrincipalName(String principalName) {
		SelectResults<ExpiringSession> results = getTemplate().find(String.format(
			FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY, getFullyQualifiedRegionName()), principalName);

		Map<String, ExpiringSession> sessions = new HashMap<String, ExpiringSession>(results.size());

		for (ExpiringSession session : results.asList()) {
			sessions.put(session.getId(), session);
		}

		return sessions;
	}

	/**
	 * Constructs a new {@link ExpiringSession} instance backed by GemFire.
	 *
	 * @return an instance of {@link ExpiringSession} backed by GemFire.
	 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository.GemFireSession
	 * @see org.springframework.session.ExpiringSession
	 */
	public ExpiringSession createSession() {
		return GemFireSession.create(getMaxInactiveIntervalInSeconds());
	}

	/**
	 * Gets a copy of an existing, non-expired {@link ExpiringSession} by ID.  If the Session is expired,
	 * then it is deleted.
	 *
	 * @param sessionId a String indicating the ID of the Session to get.
	 * @return an existing {@link ExpiringSession} by ID or null if not Session exists.
	 * @see org.springframework.session.ExpiringSession
	 * @see #delete(String)
	 */
	public ExpiringSession getSession(String sessionId) {
		ExpiringSession storedSession = getTemplate().get(sessionId);

		if (storedSession != null) {
			if (storedSession.isExpired()) {
				delete(storedSession.getId());
			}
			else {
				return GemFireSession.from(storedSession);
			}
		}

		return null;
	}

	/**
	 * Saves the specified {@link ExpiringSession} to GemFire.
	 *
	 * @param session the {@link ExpiringSession} to save.
	 * @see org.springframework.session.ExpiringSession
	 */
	public void save(ExpiringSession session) {
		getTemplate().put(session.getId(), new GemFireSession(session));
	}

	/**
	 * Deletes (removes) any existing {@link ExpiringSession} from GemFire.  This operation also results in
	 * a SessionDeletedEvent.
	 *
	 * @param sessionId a String indicating the ID of the Session to remove from GemFire.
	 * @see #handleDeleted(String, ExpiringSession)
	 */
	public void delete(String sessionId) {
		handleDeleted(sessionId, getTemplate().<Object, ExpiringSession>remove(sessionId));
	}

	/**
	 * GemFireSession is a GemFire representation model of a Spring {@link ExpiringSession} for storing and accessing
	 * Session state information in GemFire.  This class implements GemFire's {@link PdxSerializable} interface
	 * to better handle replication of Session information across the GemFire cluster.
	 *
	 * @see org.springframework.session.ExpiringSession
	 * @see com.gemstone.gemfire.pdx.PdxSerializable
	 * @see java.lang.Comparable
	 */
	public static class GemFireSession implements Comparable<ExpiringSession>, ExpiringSession, PdxSerializable {

		protected static final DateFormat TO_STRING_DATE_FORMAT = new SimpleDateFormat("YYYY-MM-dd-HH-mm-ss");

		private volatile int maxInactiveIntervalInSeconds;

		private long creationTime;
		private volatile long lastAccessedTime;

		private transient final Map<String, Object> sessionAttributes = new HashMap<String, Object>();

		private String id;

		public GemFireSession() {
			this(UUID.randomUUID().toString());
		}

		public GemFireSession(String id) {
			this.id = validateId(id);
			this.creationTime = System.currentTimeMillis();
		}

		public GemFireSession(ExpiringSession session) {
			Assert.notNull(session, "The ExpiringSession to copy cannot be null");

			this.id = session.getId();
			this.creationTime = session.getCreationTime();
			this.lastAccessedTime = session.getLastAccessedTime();
			this.maxInactiveIntervalInSeconds = session.getMaxInactiveIntervalInSeconds();

			for (String attributeName : session.getAttributeNames()) {
				sessionAttributes.put(attributeName, session.getAttribute(attributeName));
			}
		}

		public static GemFireSession create(int maxInactiveIntervalInSeconds) {
			GemFireSession session = new GemFireSession();
			session.setMaxInactiveIntervalInSeconds(maxInactiveIntervalInSeconds);
			return session;
		}

		public static GemFireSession from(ExpiringSession expiringSession) {
			GemFireSession session = new GemFireSession(expiringSession);
			session.setLastAccessedTime(System.currentTimeMillis());
			return session;
		}

		private String validateId(String id) {
			Assert.hasText(id, "ID must be specified");
			return id;
		}

		public String getId() {
			return id;
		}

		public void setAttribute(String attributeName, Object attributeValue) {
			if (attributeValue != null) {
				sessionAttributes.put(attributeName, attributeValue);
			}
			else {
				removeAttribute(attributeName);
			}
		}

		public void removeAttribute(String attributeName) {
			sessionAttributes.remove(attributeName);
		}

		public <T> T getAttribute(String attributeName) {
			return (T) sessionAttributes.get(attributeName);
		}

		public Set<String> getAttributeNames() {
			return Collections.unmodifiableSet(sessionAttributes.keySet());
		}

		public long getCreationTime() {
			return creationTime;
		}

		public boolean isExpired() {
			long lastAccessedTime = getLastAccessedTime();
			long maxInactiveIntervalInSeconds = getMaxInactiveIntervalInSeconds();

			return (maxInactiveIntervalInSeconds >= 0
				&& (System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(maxInactiveIntervalInSeconds) >= lastAccessedTime));
		}

		public  void setLastAccessedTime(long lastAccessedTime) {
			this.lastAccessedTime = lastAccessedTime;
		}

		public long getLastAccessedTime() {
			return lastAccessedTime;
		}

		public void setMaxInactiveIntervalInSeconds(final int interval) {
			this.maxInactiveIntervalInSeconds = interval;
		}

		public int getMaxInactiveIntervalInSeconds() {
			return maxInactiveIntervalInSeconds;
		}

		public void setPrincipalName(String principalName) {
			setAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME, principalName);
		}

		public String getPrincipalName() {
			return getAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME);
		}

		public void toData(PdxWriter writer) {
			writer.writeString("id", getId());
			writer.writeLong("creationTime", getCreationTime());
			writer.writeLong("lastAccessedTime", getLastAccessedTime());
			writer.writeInt("maxInactiveIntervalInSeconds", getMaxInactiveIntervalInSeconds());
			writer.writeString("principalName", getPrincipalName());
			writer.markIdentityField("id");

			Set<String> attributeNames = new HashSet<String>(nullSafeSet(getAttributeNames()));

			writer.writeObject("attributeNames", attributeNames);

			for (String attributeName : attributeNames) {
				writer.writeObject(attributeName, getAttribute(attributeName));
			}
		}

		public void fromData(PdxReader reader) {
			id = reader.readString("id");
			creationTime = reader.readLong("creationTime");
			lastAccessedTime = reader.readLong("lastAccessedTime");
			maxInactiveIntervalInSeconds = reader.readInt("maxInactiveIntervalInSeconds");
			setPrincipalName(reader.readString("principalName"));

			Set<String> attributeNames = nullSafeSet((Set<String>) reader.readObject("attributeNames"));

			for (String attributeName : attributeNames) {
				setAttribute(attributeName, reader.readObject(attributeName));
			}
		}

		<T> Set<T> nullSafeSet(Set<T> set) {
			return (set != null ? set : Collections.<T>emptySet());
		}

		@Override
		public int compareTo(ExpiringSession session) {
			return (Long.valueOf(getCreationTime()).compareTo(Long.valueOf(session.getCreationTime())));
		}

		@Override
		public boolean equals(final Object obj) {
			if (obj == this) {
				return true;
			}

			if (!(obj instanceof Session)) {
				return false;
			}

			Session that = (Session) obj;

			return getId().equals(that.getId());
		}

		@Override
		public int hashCode() {
			int hashValue = 17;
			hashValue = 37 * hashValue + getId().hashCode();
			return hashValue;
		}

		@Override
		public String toString() {
			return String.format("{ @type = %1$s, id = %2$s, creationTime = %3$s, lastAccessedTime = %4$s"
				+ ", maxInactiveIntervalInSeconds = %5$s, principalName = %6$s }", getClass().getName(), getId(),
					toString(getCreationTime()), toString(getLastAccessedTime()), getMaxInactiveIntervalInSeconds(),
						getPrincipalName());
		}

		private String toString(long timestamp) {
			return TO_STRING_DATE_FORMAT.format(new Date(timestamp));
		}
	}

}
