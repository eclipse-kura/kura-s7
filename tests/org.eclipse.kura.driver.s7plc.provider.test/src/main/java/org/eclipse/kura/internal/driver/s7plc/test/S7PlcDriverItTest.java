/*******************************************************************************
 * Copyright (c) 2026 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 *******************************************************************************/
package org.eclipse.kura.internal.driver.s7plc.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.core.configuration.metatype.Tad;
import org.eclipse.kura.driver.Driver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies that the S7 PLC Driver bundle resolves and works in a real Kura
 * framework: the Driver factory is registered by the Configuration Service and
 * an instance of it publishes a {@link Driver} service exposing the S7 channel
 * descriptor. No S7 PLC is needed, the Driver connects lazily on the first
 * read/write.
 *
 * The services are looked up through the {@link BundleContext} instead of being
 * injected with {@code @Reference}: the Kura bundles carry no
 * {@code osgi.service} capability, so a Declarative Services reference to them
 * would make the bnd resolution of this bndrun fail.
 */
@Component(immediate = true)
public class S7PlcDriverItTest {

    private static final Logger logger = LoggerFactory.getLogger(S7PlcDriverItTest.class);

    private static final String DRIVER_FACTORY_PID = "org.eclipse.kura.driver.s7plc";
    private static final String DRIVER_PID = "testS7PlcDriver";

    private static final long TIMEOUT_SECONDS = 60;

    private static final CountDownLatch activated = new CountDownLatch(1);

    // needs to be static for being available to JUnit Runner
    private static BundleContext bundleContext;
    private static ConfigurationService configurationService;
    private static Driver driver;

    @Activate
    public void activate(BundleContext context) {
        bundleContext = context;
        activated.countDown();
    }

    @BeforeClass
    public static void createDriverInstance() throws Exception {
        if (!activated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("test component not activated in " + TIMEOUT_SECONDS + " seconds");
        }

        configurationService = awaitService(ConfigurationService.class, null);

        final Map<String, Object> properties = new HashMap<>();
        properties.put("host.ip", "127.0.0.1");
        properties.put("rack", 0);
        properties.put("slot", 2);
        properties.put("authenticate", false);
        properties.put("read.minimum.gap.size", 0);

        logger.info("Creating {} instance {}...", DRIVER_FACTORY_PID, DRIVER_PID);
        configurationService.createFactoryConfiguration(DRIVER_FACTORY_PID, DRIVER_PID, properties, false);

        driver = awaitService(Driver.class, "(kura.service.pid=" + DRIVER_PID + ")");
    }

    @AfterClass
    public static void cleanup() {
        try {
            configurationService.deleteFactoryConfiguration(DRIVER_PID, false);
        } catch (Exception e) {
            logger.warn("Error deleting the Driver instance", e);
        }

        logger.info("Shutting down OSGi framework...");
        if (bundleContext != null) {
            try {
                Bundle systemBundle = bundleContext.getBundle(0);
                systemBundle.stop();
            } catch (BundleException e) {
                logger.error("Error stopping framework", e);
            }
        }
    }

    @Test
    public void shouldRegisterTheDriverFactory() throws Exception {
        assertTrue(configurationService.getFactoryComponentPids().contains(DRIVER_FACTORY_PID));
    }

    @Test
    public void shouldPublishTheDriverService() {
        assertNotNull(driver);
    }

    @Test
    public void shouldExposeTheS7ChannelDescriptor() {
        @SuppressWarnings("unchecked")
        final List<Tad> descriptor = (List<Tad>) driver.getChannelDescriptor().getDescriptor();

        assertEquals(Arrays.asList("s7.data.type", "data.block.no", "offset", "byte.count", "bit.index"),
                descriptor.stream().map(Tad::getId).collect(Collectors.toList()));
    }

    private static <T> T awaitService(final Class<T> clazz, final String filter) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline) {
            final Collection<ServiceReference<T>> references = bundleContext.getServiceReferences(clazz, filter);
            if (!references.isEmpty()) {
                return bundleContext.getService(references.iterator().next());
            }
            Thread.sleep(500);
        }

        throw new IllegalStateException("no " + clazz.getSimpleName() + " service matching " + filter + " in "
                + TIMEOUT_SECONDS + " seconds");
    }

}
