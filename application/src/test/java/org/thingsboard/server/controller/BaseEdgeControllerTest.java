/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.server.controller;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.model.ModelConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;

public abstract class BaseEdgeControllerTest extends AbstractControllerTest {

    private IdComparator<Edge> idComparator = new IdComparator<>();

    private Tenant savedTenant;
    private TenantId tenantId;
    private User tenantAdmin;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();

        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        tenantId = savedTenant.getId();
        Assert.assertNotNull(savedTenant);

        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");
        tenantAdmin.setFirstName("Joe");
        tenantAdmin.setLastName("Downs");

        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();

        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveEdge() throws Exception {
        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        Assert.assertNotNull(savedEdge);
        Assert.assertNotNull(savedEdge.getId());
        Assert.assertTrue(savedEdge.getCreatedTime() > 0);
        Assert.assertEquals(savedTenant.getId(), savedEdge.getTenantId());
        Assert.assertNotNull(savedEdge.getCustomerId());
        Assert.assertEquals(NULL_UUID, savedEdge.getCustomerId().getId());
        Assert.assertEquals(edge.getName(), savedEdge.getName());

        savedEdge.setName("My new edge");
        doPost("/api/edge", savedEdge, Edge.class);

        Edge foundEdge = doGet("/api/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(foundEdge.getName(), savedEdge.getName());
    }

    @Test
    public void testFindEdgeById() throws Exception {
        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);
        Edge foundEdge = doGet("/api/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertNotNull(foundEdge);
        Assert.assertEquals(savedEdge, foundEdge);
    }

    @Test
    public void testFindEdgeTypesByTenantId() throws Exception {
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Edge edge = constructEdge("My edge B" + i, "typeB");
            edges.add(doPost("/api/edge", edge, Edge.class));
        }
        for (int i = 0; i < 7; i++) {
            Edge edge = constructEdge("My edge C" + i, "typeC");
            edges.add(doPost("/api/edge", edge, Edge.class));
        }
        for (int i = 0; i < 9; i++) {
            Edge edge = constructEdge("My edge A" + i, "typeA");
            edges.add(doPost("/api/edge", edge, Edge.class));
        }
        List<EntitySubtype> edgeTypes = doGetTyped("/api/edge/types",
                new TypeReference<List<EntitySubtype>>() {
                });

        Assert.assertNotNull(edgeTypes);
        Assert.assertEquals(3, edgeTypes.size());
        Assert.assertEquals("typeA", edgeTypes.get(0).getType());
        Assert.assertEquals("typeB", edgeTypes.get(1).getType());
        Assert.assertEquals("typeC", edgeTypes.get(2).getType());
    }

    @Test
    public void testDeleteEdge() throws Exception {
        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        doDelete("/api/edge/" + savedEdge.getId().getId().toString())
                .andExpect(status().isOk());

        doGet("/api/edge/" + savedEdge.getId().getId().toString())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testSaveEdgeWithEmptyType() throws Exception {
        Edge edge = constructEdge("My edge", null);
        doPost("/api/edge", edge)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Edge type should be specified")));
    }

    @Test
    public void testSaveEdgeWithEmptyName() throws Exception {
        Edge edge = constructEdge(null, "default");
        doPost("/api/edge", edge)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Edge name should be specified")));
    }

    @Test
    public void testAssignUnassignEdgeToCustomer() throws Exception {
        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        Customer customer = new Customer();
        customer.setTitle("My customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        Edge assignedEdge = doPost("/api/customer/" + savedCustomer.getId().getId().toString()
                + "/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(savedCustomer.getId(), assignedEdge.getCustomerId());

        Edge foundEdge = doGet("/api/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(savedCustomer.getId(), foundEdge.getCustomerId());

        Edge unassignedEdge =
                doDelete("/api/customer/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(ModelConstants.NULL_UUID, unassignedEdge.getCustomerId().getId());

        foundEdge = doGet("/api/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(ModelConstants.NULL_UUID, foundEdge.getCustomerId().getId());
    }

    @Test
    public void testAssignEdgeToNonExistentCustomer() throws Exception {
        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        doPost("/api/customer/" + UUIDs.timeBased().toString()
                + "/edge/" + savedEdge.getId().getId().toString())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testAssignEdgeToCustomerFromDifferentTenant() throws Exception {
        loginSysAdmin();

        Tenant tenant2 = new Tenant();
        tenant2.setTitle("Different tenant");
        Tenant savedTenant2 = doPost("/api/tenant", tenant2, Tenant.class);
        Assert.assertNotNull(savedTenant2);

        User tenantAdmin2 = new User();
        tenantAdmin2.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin2.setTenantId(savedTenant2.getId());
        tenantAdmin2.setEmail("tenant3@thingsboard.org");
        tenantAdmin2.setFirstName("Joe");
        tenantAdmin2.setLastName("Downs");

        tenantAdmin2 = createUserAndLogin(tenantAdmin2, "testPassword1");

        Customer customer = new Customer();
        customer.setTitle("Different customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        login(tenantAdmin.getEmail(), "testPassword1");

        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        doPost("/api/customer/" + savedCustomer.getId().getId().toString()
                + "/edge/" + savedEdge.getId().getId().toString())
                .andExpect(status().isForbidden());

        loginSysAdmin();

        doDelete("/api/tenant/" + savedTenant2.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testFindTenantEdges() throws Exception {
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < 178; i++) {
            Edge edge = constructEdge("Edge" + i, "default");
            edges.add(doPost("/api/edge", edge, Edge.class));
        }
        List<Edge> loadedEdges = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(23);
        TextPageData<Edge> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink);
            loadedEdges.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edges, idComparator);
        Collections.sort(loadedEdges, idComparator);

        Assert.assertEquals(edges, loadedEdges);
    }

    @Test
    public void testFindTenantEdgesByName() throws Exception {
        String title1 = "Edge title 1";
        List<Edge> edgesTitle1 = new ArrayList<>();
        for (int i = 0; i < 143; i++) {
            String suffix = RandomStringUtils.randomAlphanumeric(15);
            String name = title1 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, "default");
            edgesTitle1.add(doPost("/api/edge", edge, Edge.class));
        }
        String title2 = "Edge title 2";
        List<Edge> edgesTitle2 = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            String suffix = RandomStringUtils.randomAlphanumeric(15);
            String name = title2 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, "default");
            edgesTitle2.add(doPost("/api/edge", edge, Edge.class));
        }

        List<Edge> loadedEdgesTitle1 = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(15, title1);
        TextPageData<Edge> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink);
            loadedEdgesTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edgesTitle1, idComparator);
        Collections.sort(loadedEdgesTitle1, idComparator);

        Assert.assertEquals(edgesTitle1, loadedEdgesTitle1);

        List<Edge> loadedEdgesTitle2 = new ArrayList<>();
        pageLink = new TextPageLink(4, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink);
            loadedEdgesTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edgesTitle2, idComparator);
        Collections.sort(loadedEdgesTitle2, idComparator);

        Assert.assertEquals(edgesTitle2, loadedEdgesTitle2);

        for (Edge edge : loadedEdgesTitle1) {
            doDelete("/api/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new TextPageLink(4, title1);
        pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                new TypeReference<TextPageData<Edge>>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (Edge edge : loadedEdgesTitle2) {
            doDelete("/api/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new TextPageLink(4, title2);
        pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                new TypeReference<TextPageData<Edge>>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    @Test
    public void testFindTenantEdgesByType() throws Exception {
        String title1 = "Edge title 1";
        String type1 = "typeA";
        List<Edge> edgesType1 = new ArrayList<>();
        for (int i = 0; i < 143; i++) {
            String suffix = RandomStringUtils.randomAlphanumeric(15);
            String name = title1 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, type1);
            edgesType1.add(doPost("/api/edge", edge, Edge.class));
        }
        String title2 = "Edge title 2";
        String type2 = "typeB";
        List<Edge> edgesType2 = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            String suffix = RandomStringUtils.randomAlphanumeric(15);
            String name = title2 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, type2);
            edgesType2.add(doPost("/api/edge", edge, Edge.class));
        }

        List<Edge> loadedEdgesType1 = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(15);
        TextPageData<Edge> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?type={type}&",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink, type1);
            loadedEdgesType1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edgesType1, idComparator);
        Collections.sort(loadedEdgesType1, idComparator);

        Assert.assertEquals(edgesType1, loadedEdgesType1);

        List<Edge> loadedEdgesType2 = new ArrayList<>();
        pageLink = new TextPageLink(4);
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?type={type}&",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink, type2);
            loadedEdgesType2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edgesType2, idComparator);
        Collections.sort(loadedEdgesType2, idComparator);

        Assert.assertEquals(edgesType2, loadedEdgesType2);

        for (Edge edge : loadedEdgesType1) {
            doDelete("/api/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new TextPageLink(4);
        pageData = doGetTypedWithPageLink("/api/tenant/edges?type={type}&",
                new TypeReference<TextPageData<Edge>>() {
                }, pageLink, type1);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (Edge edge : loadedEdgesType2) {
            doDelete("/api/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new TextPageLink(4);
        pageData = doGetTypedWithPageLink("/api/tenant/edges?type={type}&",
                new TypeReference<TextPageData<Edge>>() {
                }, pageLink, type2);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    @Test
    public void testFindCustomerEdges() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("Test customer");
        customer = doPost("/api/customer", customer, Customer.class);
        CustomerId customerId = customer.getId();

        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            Edge edge = constructEdge("Edge" + i, "default");
            edge = doPost("/api/edge", edge, Edge.class);
            edges.add(doPost("/api/customer/" + customerId.getId().toString()
                    + "/edge/" + edge.getId().getId().toString(), Edge.class));
        }

        List<Edge> loadedEdges = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(23);
        TextPageData<Edge> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink);
            loadedEdges.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edges, idComparator);
        Collections.sort(loadedEdges, idComparator);

        Assert.assertEquals(edges, loadedEdges);
    }

    @Test
    public void testFindCustomerEdgesByName() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("Test customer");
        customer = doPost("/api/customer", customer, Customer.class);
        CustomerId customerId = customer.getId();

        String title1 = "Edge title 1";
        List<Edge> edgesTitle1 = new ArrayList<>();
        for (int i = 0; i < 125; i++) {
            String suffix = RandomStringUtils.randomAlphanumeric(15);
            String name = title1 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, "default");
            edge = doPost("/api/edge", edge, Edge.class);
            edgesTitle1.add(doPost("/api/customer/" + customerId.getId().toString()
                    + "/edge/" + edge.getId().getId().toString(), Edge.class));
        }
        String title2 = "Edge title 2";
        List<Edge> edgesTitle2 = new ArrayList<>();
        for (int i = 0; i < 143; i++) {
            String suffix = RandomStringUtils.randomAlphanumeric(15);
            String name = title2 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, "default");
            edge = doPost("/api/edge", edge, Edge.class);
            edgesTitle2.add(doPost("/api/customer/" + customerId.getId().toString()
                    + "/edge/" + edge.getId().getId().toString(), Edge.class));
        }

        List<Edge> loadedEdgesTitle1 = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(15, title1);
        TextPageData<Edge> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink);
            loadedEdgesTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edgesTitle1, idComparator);
        Collections.sort(loadedEdgesTitle1, idComparator);

        Assert.assertEquals(edgesTitle1, loadedEdgesTitle1);

        List<Edge> loadedEdgesTitle2 = new ArrayList<>();
        pageLink = new TextPageLink(4, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink);
            loadedEdgesTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edgesTitle2, idComparator);
        Collections.sort(loadedEdgesTitle2, idComparator);

        Assert.assertEquals(edgesTitle2, loadedEdgesTitle2);

        for (Edge edge : loadedEdgesTitle1) {
            doDelete("/api/customer/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new TextPageLink(4, title1);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                new TypeReference<TextPageData<Edge>>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (Edge edge : loadedEdgesTitle2) {
            doDelete("/api/customer/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new TextPageLink(4, title2);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                new TypeReference<TextPageData<Edge>>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    @Test
    public void testFindCustomerEdgesByType() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("Test customer");
        customer = doPost("/api/customer", customer, Customer.class);
        CustomerId customerId = customer.getId();

        String title1 = "Edge title 1";
        String type1 = "typeC";
        List<Edge> edgesType1 = new ArrayList<>();
        for (int i = 0; i < 125; i++) {
            String suffix = RandomStringUtils.randomAlphanumeric(15);
            String name = title1 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, type1);
            edge = doPost("/api/edge", edge, Edge.class);
            edgesType1.add(doPost("/api/customer/" + customerId.getId().toString()
                    + "/edge/" + edge.getId().getId().toString(), Edge.class));
        }
        String title2 = "Edge title 2";
        String type2 = "typeD";
        List<Edge> edgesType2 = new ArrayList<>();
        for (int i = 0; i < 143; i++) {
            String suffix = RandomStringUtils.randomAlphanumeric(15);
            String name = title2 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, type2);
            edge = doPost("/api/edge", edge, Edge.class);
            edgesType2.add(doPost("/api/customer/" + customerId.getId().toString()
                    + "/edge/" + edge.getId().getId().toString(), Edge.class));
        }

        List<Edge> loadedEdgesType1 = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(15);
        TextPageData<Edge> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?type={type}&",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink, type1);
            loadedEdgesType1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edgesType1, idComparator);
        Collections.sort(loadedEdgesType1, idComparator);

        Assert.assertEquals(edgesType1, loadedEdgesType1);

        List<Edge> loadedEdgesType2 = new ArrayList<>();
        pageLink = new TextPageLink(4);
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?type={type}&",
                    new TypeReference<TextPageData<Edge>>() {
                    }, pageLink, type2);
            loadedEdgesType2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edgesType2, idComparator);
        Collections.sort(loadedEdgesType2, idComparator);

        Assert.assertEquals(edgesType2, loadedEdgesType2);

        for (Edge edge : loadedEdgesType1) {
            doDelete("/api/customer/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new TextPageLink(4);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?type={type}&",
                new TypeReference<TextPageData<Edge>>() {
                }, pageLink, type1);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (Edge edge : loadedEdgesType2) {
            doDelete("/api/customer/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new TextPageLink(4);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?type={type}&",
                new TypeReference<TextPageData<Edge>>() {
                }, pageLink, type2);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

}
