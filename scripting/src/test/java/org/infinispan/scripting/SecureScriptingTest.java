package org.infinispan.scripting;

import static org.testng.AssertJUnit.assertEquals;

import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.ExecutionException;
import javax.security.auth.Subject;

import org.infinispan.Cache;
import org.infinispan.commons.CacheException;
import org.infinispan.configuration.cache.AuthorizationConfigurationBuilder;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalAuthorizationConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.security.AuthorizationPermission;
import org.infinispan.security.Security;
import org.infinispan.security.impl.IdentityRoleMapper;
import org.infinispan.tasks.TaskContext;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "scripting.SecureScriptingTest")
public class SecureScriptingTest extends AbstractScriptingTest {

   static final Subject ADMIN = TestingUtil.makeSubject("admin", ScriptingManager.SCRIPT_MANAGER_ROLE);
   static final Subject RUNNER = TestingUtil.makeSubject("runner", "runner");
   static final Subject PHEIDIPPIDES = TestingUtil.makeSubject("pheidippides", "pheidippides");
   static final String CACHE_NAME = "secured-script-exec";

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();
      GlobalAuthorizationConfigurationBuilder globalRoles = global.security().authorization().enable().principalRoleMapper(new IdentityRoleMapper());
      ConfigurationBuilder config = TestCacheManagerFactory.getDefaultCacheConfiguration(true);
      AuthorizationConfigurationBuilder authConfig = config.security().authorization().enable();

      globalRoles
         .role("runner")
            .permission(AuthorizationPermission.EXEC)
            .permission(AuthorizationPermission.READ)
            .permission(AuthorizationPermission.WRITE)
         .role("pheidippides")
            .permission(AuthorizationPermission.EXEC)
            .permission(AuthorizationPermission.READ)
            .permission(AuthorizationPermission.WRITE)
         .role("admin")
            .permission(AuthorizationPermission.ALL);
      authConfig.role("runner").role("pheidippides").role("admin");
      EmbeddedCacheManager cm = TestCacheManagerFactory.createCacheManager(global, config);
      Security.doAs(ADMIN, new PrivilegedExceptionAction<Void>() {
         @Override
         public Void run() throws Exception {
            cm.getCache(ScriptingTest.CACHE_NAME);
            cm.getCache(SecureScriptingTest.CACHE_NAME);
            return null;
         }
      });

      return cm;
   }

   @Override
   protected String[] getScripts() {
      return new String[] { "test.js", "testRole.js", "testRoleWithCache.js" };
   }

   @Override
   protected void setup() throws Exception {
      Security.doAs(ADMIN, new PrivilegedExceptionAction<Void>() {
         @Override
         public Void run() throws Exception {
            SecureScriptingTest.super.setup();
            cacheManager.defineConfiguration("nonSecuredCache", TestCacheManagerFactory.getDefaultCacheConfiguration(true).build());
            return null;
         }
      });
   }

   @Override
   protected void teardown() {
      Security.doAs(ADMIN, new PrivilegedAction<Void>() {
         @Override
         public Void run() {
            SecureScriptingTest.super.teardown();
            return null;
         }
      });
   }

   @Override
   protected void clearContent() {
      Security.doAs(ADMIN, new PrivilegedAction<Void>() {
         @Override
         public Void run() {
            cacheManager.getCache().clear();
            return null;
         }
      });
   }

   @Test(expectedExceptions= { SecurityException.class, CacheException.class} )
   public void testSimpleScript() throws Exception {
      String result = (String) scriptingManager.runScript("test.js", new TaskContext().addParameter("a", "a").cache(cache())).get();
      assertEquals("a", result);
   }

   public void testSimpleScriptWithEXECPermissions() throws Exception {
      String result = Security.doAs(RUNNER, new PrivilegedExceptionAction<String>() {
         @Override
         public String run() throws Exception {
            return (String) scriptingManager.runScript("test.js", new TaskContext().addParameter("a", "a").cache(cache())).get();
         }
      });
      assertEquals("a", result);
   }

   @Test(expectedExceptions= { PrivilegedActionException.class, CacheException.class} )
   public void testSimpleScriptWithEXECPermissionsWrongRole() throws Exception {
      String result = Security.doAs(RUNNER, new PrivilegedExceptionAction<String>() {
         @Override
         public String run() throws Exception {
            return (String) scriptingManager.runScript("testRole.js", new TaskContext().addParameter("a", "a").cache(cache())).get();
         }
      });
      assertEquals("a", result);
   }

   public void testSimpleScriptWithEXECPermissionsRightRole() throws Exception {
      String result = Security.doAs(PHEIDIPPIDES, new PrivilegedExceptionAction<String>() {
         @Override
         public String run() throws Exception {
            return (String) scriptingManager.runScript("testRole.js", new TaskContext().addParameter("a", "a").cache(cache())).get();
         }
      });
      assertEquals("a", result);
   }

   public void testScriptOnNonSecuredCache() throws ExecutionException, InterruptedException, PrivilegedActionException {
      Cache<String, String> nonSecCache = cache("nonSecuredCache");
      nonSecCache.put("a", "value");
      assertEquals("value", nonSecCache.get("a"));

      String result = Security.doAs(PHEIDIPPIDES, new PrivilegedExceptionAction<String>() {
         @Override
         public String run() throws Exception {
            return (String) scriptingManager.runScript("testRoleWithCache.js", new TaskContext().addParameter("a", "a").cache(nonSecCache)).get();
         }
      });
      assertEquals("a", result);
      assertEquals("a", nonSecCache.get("a"));
   }

   @Test(expectedExceptions= { PrivilegedActionException.class, CacheException.class} )
   public void testScriptOnNonSecuredCacheWrongRole() throws ExecutionException, InterruptedException, PrivilegedActionException {
      String result = Security.doAs(RUNNER, new PrivilegedExceptionAction<String>() {
         @Override
         public String run() throws Exception {
            return (String) scriptingManager.runScript("testRoleWithCache.js", new TaskContext().addParameter("a", "a").cache(cache("nonSecuredCache"))).get();
         }
      });
   }

}
