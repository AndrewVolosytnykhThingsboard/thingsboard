package org.thingsboard.server.dao.service;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.RpcId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.rpc.Rpc;
import org.thingsboard.server.common.data.rpc.RpcStatus;
import org.thingsboard.server.dao.rpc.RpcService;

import java.util.UUID;

public class BaseRpcServiceSqlTest extends AbstractServiceTest {
    @Autowired
    private RpcService rpcService;

    private TenantId tenantId;
    private DeviceProfile deviceProfile;
    private Device device;

    @Before
    public void before() {
        Tenant tenant = new Tenant();
        tenant.setTitle("Testing tenant");
        Tenant savedTenant = tenantService.saveTenant(tenant);
        Assert.assertNotNull(savedTenant);
        tenantId = savedTenant.getId();

        DeviceProfile newDeviceProfile = super.createDeviceProfile(tenantId, "Device Profile");
        deviceProfile = deviceProfileService.saveDeviceProfile(newDeviceProfile);

        Device rawDevice = new Device();
        rawDevice.setTenantId(tenantId);
        rawDevice.setName("Testing device");
        rawDevice.setType("default");
        rawDevice.setDeviceProfileId(deviceProfile.getId());
        device = deviceService.saveDevice(rawDevice);
    }

    @After
    public void after() {
        tenantService.deleteTenant(tenantId);
    }

    @Test
    public void testSaveRpcCorrectValidation() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(tenantId);
        rpc.setDeviceId(device.getId());
        rpc.setExpirationTime(30L);
        rpc.setRequest(mapper.readTree("\t{\n" +
                "\t\t\"msg\": {\n" +
                "\t\t\t\"method\": \"setGpio\",\n" +
                "\t\t\t\"params\": {\n" +
                "\t\t\t\t\"pin\": \"23\",\n" +
                "\t\t\t\t\"value\": 1\n" +
                "\t\t\t}\n" +
                "\t\t},\n" +
                "\t\t\"metadata\": {\n" +
                "\t\t\t\"persisted\": true\n" +
                "\t\t},\n" +
                "\t\t\"msgType\": \"POST_TELEMETRY_REQUEST\"\n" +
                "\t}\n" +
                "\t"));
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        Rpc savedRpc = rpcService.save(rpc);
        Assert.assertNotNull(savedRpc);
        Assert.assertEquals(savedRpc.getDeviceId(), device.getId());
        Assert.assertEquals(savedRpc.getRequest(), rpc.getRequest());
        Assert.assertEquals(rpcId, savedRpc.getId());
    }

    @Test
    public void testRpcCantSaveWithNoTenantId() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(null);
        rpc.setDeviceId(device.getId());
        rpc.setExpirationTime(30L);
        rpc.setRequest(mapper.readTree("{}"));
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        try {
            rpcService.save(rpc);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "Tenant ID should be specified!");
        }
    }

    @Test
    public void testRpcCantSaveWithNonExistingTenant() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(new TenantId(UUID.randomUUID()));
        rpc.setDeviceId(device.getId());
        rpc.setExpirationTime(30L);
        rpc.setRequest(mapper.readTree("{}"));
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        try {
            rpcService.save(rpc);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "RPC is referencing to non-existing tenant!");
        }
    }

    @Test
    public void testRpcCantSaveWithNoDevice() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(tenantId);
        rpc.setDeviceId(null);
        rpc.setExpirationTime(30L);
        rpc.setRequest(mapper.readTree("{}"));
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        try {
            rpcService.save(rpc);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "Device ID should be specified!");
        }
    }

    @Test
    public void testRpcCantSaveWithNonExistingDevice() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(tenantId);
        rpc.setDeviceId(new DeviceId(UUID.randomUUID()));
        rpc.setExpirationTime(30L);
        rpc.setRequest(mapper.readTree("{}"));
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        try {
            rpcService.save(rpc);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "RPC is referencing to non-existing device!");
        }
    }

    @Test
    public void testRpcCantSaveWithNoRequest() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(tenantId);
        rpc.setDeviceId(device.getId());
        rpc.setExpirationTime(30L);
        rpc.setRequest(null);
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        try {
            rpcService.save(rpc);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "RPC request should be not empty!");
        }
    }

    @Test
    public void testRpcCantSaveWithNoStatus() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(tenantId);
        rpc.setDeviceId(device.getId());
        rpc.setExpirationTime(30L);
        rpc.setRequest(mapper.readTree("{}"));
        rpc.setStatus(null);
        rpc.setId(rpcId);

        try {
            rpcService.save(rpc);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "RPC status should be not empty!");
        }
    }

    @Test
    public void testRpcCantSaveWithIncorrectExpirationTime() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(tenantId);
        rpc.setDeviceId(device.getId());
        rpc.setExpirationTime(0L);
        rpc.setRequest(mapper.readTree("{}"));
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        try {
            rpcService.save(rpc);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "Expiration time should be more than 0!");
        }
    }

    @Test
    public void testRpcStatusAndRequestCanBeUpdated() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(tenantId);
        rpc.setDeviceId(device.getId());
        rpc.setExpirationTime(30L);
        rpc.setRequest(mapper.readTree("{}"));
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        Rpc saved = rpcService.save(rpc);
        saved.setStatus(RpcStatus.SUCCESSFUL);
        saved.setResponse(mapper.readTree("{}"));
        Rpc updated = rpcService.save(saved);

        Assert.assertEquals(rpc.getId(), updated.getId());
        Assert.assertNotNull(updated.getResponse());
        Assert.assertNotEquals(rpc.getStatus(), updated.getStatus());
    }

    @Test
    public void testRpcResponseCanBeUpdatedAtOnce() throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(tenantId);
        rpc.setDeviceId(device.getId());
        rpc.setExpirationTime(30L);
        rpc.setRequest(mapper.readTree("{}"));
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        Rpc saved = rpcService.save(rpc);
        saved.setResponse(mapper.readTree("{}"));
        Rpc updated = rpcService.save(saved);
        updated.setResponse(mapper.readTree("{\"data\": \"lol\"}"));

        try {
            rpcService.save(updated);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "Can't update RPC response!");
        }

    }

    @Test
    public void testRpcFieldsCantBeUpdated()  throws Exception {
        RpcId rpcId = new RpcId(UUID.randomUUID());

        Rpc rpc = new Rpc();
        rpc.setTenantId(tenantId);
        rpc.setDeviceId(device.getId());
        rpc.setExpirationTime(30L);
        rpc.setRequest(mapper.readTree("{}"));
        rpc.setStatus(RpcStatus.QUEUED);
        rpc.setId(rpcId);

        Rpc saved = rpcService.save(rpc);
        saved.setTenantId(new TenantId(UUID.randomUUID()));

        try {
            rpcService.save(saved);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "Can't update Tenant id!");
        }

        saved = new Rpc(rpc);
        saved.setDeviceId(new DeviceId(UUID.randomUUID()));

        try {
            rpcService.save(saved);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "Can't update Device id!");
        }

        saved = new Rpc(rpc);
        saved.setExpirationTime(15645L);

        try {
            rpcService.save(saved);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "Can't update RPC expiration time!");
        }

        saved = new Rpc(rpc);
        saved.setRequest(mapper.readTree("{\"somethingnew\": \"what a way did you have to find this line?\"}"));

        try {
            rpcService.save(saved);
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "Can't update RPC request!");
        }

    }

}
