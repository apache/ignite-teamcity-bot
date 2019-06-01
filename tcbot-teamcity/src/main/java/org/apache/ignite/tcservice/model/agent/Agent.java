/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.tcservice.model.agent;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.ignite.tcservice.model.Properties;
import org.apache.ignite.tcservice.model.hist.BuildRef;

/**
 * <p>Java class for agent complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="agent">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{}properties" minOccurs="0"/>
 *         &lt;element name="pool" type="{}agentPool-ref" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="href" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="name" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="typeId" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="connected" use="required" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *       &lt;attribute name="enabled" use="required" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *       &lt;attribute name="authorized" use="required" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *       &lt;attribute name="uptodate" use="required" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *       &lt;attribute name="ip" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="locator" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlRootElement(name = "agent")
@XmlAccessorType(XmlAccessType.FIELD)
public class Agent {
    protected Properties properties;
    protected AgentPoolRef pool;
    @XmlAttribute(name = "href")
    protected String href;
    @XmlAttribute(name = "id")
    protected Integer id;
    @XmlAttribute(name = "name")
    protected String name;
    @XmlAttribute(name = "typeId")
    protected Integer typeId;
    @XmlAttribute(name = "connected", required = true)
    protected boolean connected;
    @XmlAttribute(name = "enabled", required = true)
    protected boolean enabled;
    @XmlAttribute(name = "authorized", required = true)
    protected boolean authorized;
    @XmlAttribute(name = "uptodate", required = true)
    protected boolean uptodate;
    @XmlAttribute(name = "ip")
    protected String ip;
    @XmlAttribute(name = "locator")
    protected String locator;
    @XmlElement(name = "build")
    protected BuildRef build;

    /**
     * Gets the value of the properties property.
     *
     * @return
     *     possible object is
     *     {@link Properties }
     *
     */
    public Properties getProperties() {
        return properties;
    }

    /**
     * Sets the value of the properties property.
     *
     * @param value
     *     allowed object is
     *     {@link Properties }
     *
     */
    public void setProperties(Properties value) {
        this.properties = value;
    }

    /**
     * Gets the value of the pool property.
     *
     * @return
     *     possible object is
     *     {@link AgentPoolRef }
     *
     */
    public AgentPoolRef getPool() {
        return pool;
    }

    /**
     * Sets the value of the pool property.
     *
     * @param value
     *     allowed object is
     *     {@link AgentPoolRef }
     *
     */
    public void setPool(AgentPoolRef value) {
        this.pool = value;
    }

    /**
     * Gets the value of the href property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getHref() {
        return href;
    }

    /**
     * Sets the value of the href property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setHref(String value) {
        this.href = value;
    }

    /**
     * Gets the value of the id property.
     *
     * @return
     *     possible object is
     *     {@link Integer }
     *
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     *
     * @param value
     *     allowed object is
     *     {@link Integer }
     *
     */
    public void setId(Integer value) {
        this.id = value;
    }

    /**
     * Gets the value of the name property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the typeId property.
     *
     * @return
     *     possible object is
     *     {@link Integer }
     *
     */
    public Integer getTypeId() {
        return typeId;
    }

    /**
     * Sets the value of the typeId property.
     *
     * @param value
     *     allowed object is
     *     {@link Integer }
     *
     */
    public void setTypeId(Integer value) {
        this.typeId = value;
    }

    /**
     * Gets the value of the connected property.
     *
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Sets the value of the connected property.
     *
     */
    public void setConnected(boolean value) {
        this.connected = value;
    }

    /**
     * Gets the value of the enabled property.
     *
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the value of the enabled property.
     *
     */
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    /**
     * Gets the value of the authorized property.
     *
     */
    public boolean isAuthorized() {
        return authorized;
    }

    /**
     * Sets the value of the authorized property.
     *
     */
    public void setAuthorized(boolean value) {
        this.authorized = value;
    }

    /**
     * Gets the value of the uptodate property.
     *
     */
    public boolean isUptodate() {
        return uptodate;
    }

    /**
     * Sets the value of the uptodate property.
     *
     */
    public void setUptodate(boolean value) {
        this.uptodate = value;
    }

    /**
     * Gets the value of the ip property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getIp() {
        return ip;
    }

    /**
     * Sets the value of the ip property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setIp(String value) {
        this.ip = value;
    }

    /**
     * Gets the value of the locator property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getLocator() {
        return locator;
    }

    /**
     * Sets the value of the locator property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setLocator(String value) {
        this.locator = value;
    }

    /**
     * @param build Build.
     */
    public void setBuild(BuildRef build) {
        this.build = build;
    }

    /**
     * @return build.
     */
    public BuildRef getBuild() {
        return build;
    }
}
