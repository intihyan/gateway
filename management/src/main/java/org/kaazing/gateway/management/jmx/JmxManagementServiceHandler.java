/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.management.jmx;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.kaazing.gateway.management.ManagementServiceHandler;
import org.kaazing.gateway.management.config.ClusterConfigurationBean;
import org.kaazing.gateway.management.config.NetworkConfigurationBean;
import org.kaazing.gateway.management.config.RealmConfigurationBean;
import org.kaazing.gateway.management.config.SecurityConfigurationBean;
import org.kaazing.gateway.management.config.ServiceConfigurationBean;
import org.kaazing.gateway.management.config.ServiceDefaultsConfigurationBean;
import org.kaazing.gateway.management.context.ManagementContext;
import org.kaazing.gateway.management.gateway.GatewayManagementBean;
import org.kaazing.gateway.management.service.ServiceManagementBean;
import org.kaazing.gateway.management.session.SessionManagementBean;
import org.kaazing.gateway.management.system.CpuListManagementBean;
import org.kaazing.gateway.management.system.CpuManagementBean;
import org.kaazing.gateway.management.system.HostManagementBean;
import org.kaazing.gateway.management.system.JvmManagementBean;
import org.kaazing.gateway.management.system.NicListManagementBean;
import org.kaazing.gateway.management.system.NicManagementBean;
import org.kaazing.gateway.resource.address.ResourceAddress;
import org.kaazing.gateway.server.Gateway;
import org.kaazing.gateway.service.ServiceContext;
import org.kaazing.gateway.transport.BridgeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Kaazing service that implements JMX management support.
 */
class JmxManagementServiceHandler implements ManagementServiceHandler {

    private static final String LOGGER_NAME = "management.jmx";
    private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    public static final String JMX_OBJECT_NAME = Gateway.class.getPackage().getName() + ".management";
    private static final String GATEWAY_MBEAN_FORMAT_STR = "%s:root=gateways,gatewayId=%s,name=summary";
    private static final String SERVICE_MBEAN_FORMAT_STR =
            "%s:root=gateways,gatewayId=%s,subtype=services,serviceType=%s,serviceId=\"%s\",name=summary";
    private static final String SESSION_MBEAN_FORMAT_STR =
            "%s:root=gateways,gatewayId=%s,subtype=services,serviceType=%s,serviceId=\"%s\",name=sessions,sessionId=id-%d";
    private static final String CLUSTER_CONFIG_MBEAN_FORMAT_STR =
            "%s:root=gateways,gatewayId=%s,subtype=configuration,name=cluster,clusterName=%s";
    private static final String NETWORK_MAPPING_MBEAN_FORMAT_STR =
            "%s:root=gateways,gatewayId=%s,subtype=configuration,name=network-mappings,id=%d";
    private static final String SECURITY_MBEAN_FORMAT_STR = "%s:root=gateways,gatewayId=%s,subtype=configuration,name=security";
    private static final String REALM_MBEAN_FORMAT_STR =
            "%s:root=gateways,gatewayId=%s,subtype=configuration,name=realms,realm=%s";
    private static final String SERVICE_CONFIG_MBEAN_FORMAT_STR =
            "%s:root=gateways,gatewayId=%s,subtype=configuration,name=services,serviceType=%s,id=%d";
    private static final String SYSTEM_MBEAN_FORMAT_STR = "%s:root=gateways,gatewayId=%s,subtype=system,name=summary";
    private static final String CPU_LIST_MBEAN_FORMAT_STR = "%s:root=gateways,gatewayId=%s,subtype=system,name=CPUs/cores";
    private static final String NIC_LIST_MBEAN_FORMAT_STR = "%s:root=gateways,gatewayId=%s,subtype=system,name=NICs";
    private static final String JVM_MBEAN_FORMAT_STR = "%s:root=gateways,gatewayId=%s,subtype=jvm,name=summary";
    private static final String SERVICE_DEFAULTS_CONFIG_MBEAN_FORMAT_STR =
            "%s:root=gateways,gatewayId=%s,subtype=configuration,name=service-defaults";
    private static final String VERSION_INFO_MBEAN_FORMAT_STR =
            "%s:root=gateways,gatewayId=%s,subtype=configuration,name=version-info";
    private static final String CPU_MBEAN_FORMAT_STR = "%s:root=gateways,gatewayId=%s,subtype=system,name=CPUs/cores,id=%d";
    private static final String NIC_MBEAN_FORMAT_STR = "%s:root=gateways,gatewayId=%s,subtype=system,name=NICs,interfaceName=%s";

    private final AtomicLong notificationSequenceNumber = new AtomicLong(0);
    // For performance, I need to pass this to the agent
    protected final ServiceContext serviceContext;
    private final ManagementContext managementContext;
    private final MBeanServer mbeanServer;

    private final Map<Integer, ServiceMXBean> serviceBeanMap;
    private final Map<Long, SessionMXBean> sessionBeanMap;

    public JmxManagementServiceHandler(ServiceContext serviceContext, ManagementContext managementContext, MBeanServer
            mbeanServer) {
        this.serviceContext = serviceContext;
        this.managementContext = managementContext;
        this.mbeanServer = mbeanServer;
        serviceBeanMap = new HashMap<>();
        sessionBeanMap = new HashMap<>();

        managementContext.addGatewayManagementListener(new JmxGatewayManagementListener(this));
        managementContext.addServiceManagementListener(new JmxServiceManagementListener(this));
        managementContext.addSessionManagementListener(new JmxSessionManagementListener(this));
    }

    protected long nextNotificationSequenceNumber() {
        return notificationSequenceNumber.getAndIncrement();
    }

    @Override
    public ServiceContext getServiceContext() {
        return serviceContext;
    }

    public ServiceMXBean getServiceMXBean(int serviceId) {
        return serviceBeanMap.get(serviceId);
    }

    public SessionMXBean getSessionMXBean(long sessionId) {
        return sessionBeanMap.get(sessionId);
    }

    @Override
    public void addGatewayManagementBean(GatewayManagementBean gatewayManagementBean) {
        try {
            String hostAndPid = gatewayManagementBean.getHostAndPid();

            ObjectName name =
                    new ObjectName(String.format(GATEWAY_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            hostAndPid));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Gateway MBean name %s already registered", name));
            } else {
                GatewayMXBeanImpl gatewayMXBean = new GatewayMXBeanImpl(name, gatewayManagementBean);

                mbeanServer.registerMBean(gatewayMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addServiceManagementBean(ServiceManagementBean serviceManagementBean) {
        try {
            GatewayManagementBean gatewayManagementBean = serviceManagementBean.getGatewayManagementBean();

            ObjectName name =
                    new ObjectName(String.format(SERVICE_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid(),
                            replaceCharactersDisallowedInObjectName(serviceManagementBean.getServiceType()),
                            serviceManagementBean.getServiceName()
                    ));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Service MBean name %s already registered", name));

            } else {
                ServiceMXBeanImpl serviceMXBean = new ServiceMXBeanImpl(this, name, serviceManagementBean);
                mbeanServer.registerMBean(serviceMXBean, name);
                serviceBeanMap.put(serviceManagementBean.getId(), serviceMXBean);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addSessionManagementBean(SessionManagementBean sessionManagementBean) {
        try {
            ServiceManagementBean serviceManagementBean = sessionManagementBean.getServiceManagementBean();
            GatewayManagementBean gatewayManagementBean = serviceManagementBean.getGatewayManagementBean();
            ResourceAddress address = BridgeSession.LOCAL_ADDRESS.get(sessionManagementBean.getSession());

            ObjectName name =
                    new ObjectName(String.format(SESSION_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid(),
                            replaceCharactersDisallowedInObjectName(serviceManagementBean.getServiceType()),
                            address.getExternalURI(),
                            sessionManagementBean.getId()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Service MBean name %s already registered", name));
            } else {
                SessionMXBeanImpl sessionMXBean = new SessionMXBeanImpl(name, sessionManagementBean);
                mbeanServer.registerMBean(sessionMXBean, name);
                sessionBeanMap.put(sessionManagementBean.getId(), sessionMXBean);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void removeSessionManagementBean(SessionManagementBean sessionManagementBean) {
        try {
            SessionMXBean sessionMXBean = sessionBeanMap.remove(sessionManagementBean.getId());
            if (sessionMXBean != null) {
                ObjectName name = sessionMXBean.getObjectName();
                if (mbeanServer.isRegistered(name)) {
                    mbeanServer.unregisterMBean(name);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addClusterConfigurationBean(ClusterConfigurationBean clusterConfigBean) {
        try {
            GatewayManagementBean gatewayManagementBean = clusterConfigBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(CLUSTER_CONFIG_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid(),
                            ObjectName.quote(clusterConfigBean.getName())));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Cluster config MBean name %s already registered", name));

            } else {
                ClusterConfigurationMXBean clusterConfigMXBean = new ClusterConfigurationMXBeanImpl(name, clusterConfigBean);
                mbeanServer.registerMBean(clusterConfigMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addNetworkConfigurationBean(NetworkConfigurationBean networkMappingBean) {
        try {
            GatewayManagementBean gatewayManagementBean = networkMappingBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(NETWORK_MAPPING_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid(),
                            networkMappingBean.getId()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Network mapping MBean name %s already registered", name));

            } else {
                NetworkMappingMXBean networkMappingMXBean = new NetworkMappingMXBeanImpl(name, networkMappingBean);
                mbeanServer.registerMBean(networkMappingMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addSecurityConfigurationBean(SecurityConfigurationBean securityBean) {
        try {
            GatewayManagementBean gatewayManagementBean = securityBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(SECURITY_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Realm MBean name %s already registered", name));

            } else {
                SecurityMXBean securityMXBean = new SecurityMXBeanImpl(name, securityBean);
                mbeanServer.registerMBean(securityMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addRealmConfigurationBean(RealmConfigurationBean realmBean) {
        try {
            GatewayManagementBean gatewayManagementBean = realmBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(REALM_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid(),
                            realmBean.getName()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Realm MBean name %s already registered", name));

            } else {
                RealmMXBean realmMXBean = new RealmMXBeanImpl(name, realmBean);
                mbeanServer.registerMBean(realmMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addServiceConfigurationBean(ServiceConfigurationBean serviceConfigurationBean) {
        try {
            GatewayManagementBean gatewayManagementBean = serviceConfigurationBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(SERVICE_CONFIG_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid(),
                            replaceCharactersDisallowedInObjectName(serviceConfigurationBean.getType()),
                            serviceConfigurationBean.getId()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Service config MBean name %s already registered", name));

            } else {
                ServiceConfigurationMXBean serviceConfigurationMXBean =
                        new ServiceConfigurationMXBeanImpl(name, serviceConfigurationBean);
                mbeanServer.registerMBean(serviceConfigurationMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addServiceDefaultsConfigurationBean(ServiceDefaultsConfigurationBean serviceDefaultsConfigurationBean) {
        try {
            GatewayManagementBean gatewayManagementBean = serviceDefaultsConfigurationBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(SERVICE_DEFAULTS_CONFIG_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Service defaults config MBean name %s already registered", name));

            } else {
                ServiceDefaultsConfigurationMXBean serviceDefaultsConfigurationMXBean =
                        new ServiceDefaultsConfigurationMXBeanImpl(name, serviceDefaultsConfigurationBean);
                mbeanServer.registerMBean(serviceDefaultsConfigurationMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    @Override
    public void addVersionInfo(GatewayManagementBean gatewayManagementBean) {
        try {
            ObjectName name =
                    new ObjectName(String.format(VERSION_INFO_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Version info MBean name %s already registered", name));

            } else {
                VersionInfoMXBean versionInfoMXBean =
                        new VersionInfoMXBeanImpl(name, gatewayManagementBean);
                mbeanServer.registerMBean(versionInfoMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addSystemManagementBean(HostManagementBean systemManagementBean) {
        try {
            GatewayManagementBean gatewayManagementBean = systemManagementBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(SYSTEM_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Gateway system MBean name %s already registered", name));

            } else {
                HostMXBeanImpl systemMXBean = new HostMXBeanImpl(name, systemManagementBean);
                mbeanServer.registerMBean(systemMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addCpuListManagementBean(CpuListManagementBean cpuListManagementBean) {
        try {
            GatewayManagementBean gatewayManagementBean = cpuListManagementBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(CPU_LIST_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Gateway system MBean name %s already registered", name));

            } else {
                CpuListMXBeanImpl cpuListMXBean = new CpuListMXBeanImpl(name, cpuListManagementBean);
                mbeanServer.registerMBean(cpuListMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addNicListManagementBean(NicListManagementBean nicListManagementBean) {
        try {
            GatewayManagementBean gatewayManagementBean = nicListManagementBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(NIC_LIST_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Gateway system MBean name %s already registered", name));

            } else {
                NicListMXBeanImpl nicListMXBean = new NicListMXBeanImpl(name, nicListManagementBean);
                mbeanServer.registerMBean(nicListMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addCpuManagementBean(CpuManagementBean cpuManagementBean, String hostAndPid) {
        try {
            ObjectName name =
                    new ObjectName(String.format(CPU_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            hostAndPid,
                            cpuManagementBean.getId()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Gateway system CPU MBean name %s already registered", name));

            } else {
                CpuMXBeanImpl cpuMXBean = new CpuMXBeanImpl(name, cpuManagementBean);
                mbeanServer.registerMBean(cpuMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addNicManagementBean(NicManagementBean nicManagementBean, String hostAndPid) {
        try {
            // The NIC interface name may contain a ':' (for sub-interfaces, like eth0:1).
            // That's illegal for JMX names, so we have to change it to not contain colons.
            // We'll substitute a '_' for ':'.
            String nicName = nicManagementBean.getName().replace(':', '_');

            ObjectName name =
                    new ObjectName(String.format(NIC_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            hostAndPid,
                            nicName));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Gateway system NetInterface MBean name %s already registered", name));

            } else {
                NicMXBeanImpl nicMXBean = new NicMXBeanImpl(name, nicManagementBean);
                mbeanServer.registerMBean(nicMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void addJvmManagementBean(JvmManagementBean jvmManagementBean) {
        try {
            GatewayManagementBean gatewayManagementBean = jvmManagementBean.getGatewayManagementBean();
            ObjectName name =
                    new ObjectName(String.format(JVM_MBEAN_FORMAT_STR,
                            JMX_OBJECT_NAME,
                            gatewayManagementBean.getHostAndPid()));
            if (mbeanServer.isRegistered(name)) {
                LOGGER.warn(String.format("Gateway JVM MBean name %s already registered", name));

            } else {
                JvmMXBeanImpl jvmMXBean = new JvmMXBeanImpl(name, jvmManagementBean);
                mbeanServer.registerMBean(jvmMXBean, name);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void cleanupRegisteredBeans() {
        String gatewayId = ManagementFactory.getRuntimeMXBean().getName();

        // These query strings are tied to the constants at the top of the file and are sensitive to changes in those format
        // strings
        cleanupRegisteredBeans("%s:root=gateways,gatewayId=%s,name=summary", gatewayId);
        cleanupRegisteredBeans("%s:root=gateways,gatewayId=%s,subtype=services,*,name=summary", gatewayId);
        cleanupRegisteredBeans("%s:root=gateways,gatewayId=%s,subtype=configuration,*", gatewayId);
        cleanupRegisteredBeans("%s:root=gateways,gatewayId=%s,subtype=system,*", gatewayId);
        cleanupRegisteredBeans("%s:root=gateways,gatewayId=%s,subtype=jvm,name=summary", gatewayId);
    }

    private void cleanupRegisteredBeans(String formatString, String gatewayId) {
        try {
            ObjectName query = new ObjectName(String.format(formatString, JMX_OBJECT_NAME, gatewayId));

            Set<ObjectName> beanNames = mbeanServer.queryNames(null, query);
            for (ObjectName beanName : beanNames) {
                if (mbeanServer.isRegistered(beanName)) {
                    mbeanServer.unregisterMBean(beanName);
                }
            }
        } catch (JMException ex) {
            // this is cleanup, so just silently ignore
        }
    }

    // We allow service type in form $class:my.package.MyClass$ for internal testing purposes
    // We must replace the colon, otherwise new ObjectName(...) throws an exception
    private String replaceCharactersDisallowedInObjectName(String name) {
        return name.replace(':', '_');
    }

}
