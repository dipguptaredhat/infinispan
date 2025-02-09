package org.infinispan.client.hotrod.maxresult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.configuration.cache.IndexStorage.LOCAL_HEAP;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.Search;
import org.infinispan.client.hotrod.test.SingleHotRodServerTest;
import org.infinispan.commons.test.annotation.TestForIssue;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.query.dsl.QueryResult;
import org.infinispan.query.model.Game;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "org.infinispan.client.hotrod.maxresult.RemoteHitCountAccuracyTest")
@TestForIssue(jiraKey = "ISPN-14195")
public class RemoteHitCountAccuracyTest extends SingleHotRodServerTest {

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      ConfigurationBuilder indexed = new ConfigurationBuilder();
      indexed.indexing().enable()
            .storage(LOCAL_HEAP)
            .addIndexedEntity("Game");
      indexed.query().hitCountAccuracy(10); // lower the default accuracy

      EmbeddedCacheManager manager = TestCacheManagerFactory.createServerModeCacheManager();
      manager.defineConfiguration("indexed-games", indexed.build());
      return manager;
   }

   @Override
   protected SerializationContextInitializer contextInitializer() {
      return Game.GameSchema.INSTANCE;
   }

   @Test
   public void overrideHitCountAccuracy() {
      RemoteCache<Integer, Game> games = remoteCacheManager.getCache("indexed-games");
      for (int i = 1; i <= 5000; i++) {
         games.put(i, new Game("Game " + i, "This is the game " + i + "# of a series"));
      }

      QueryFactory factory = Search.getQueryFactory(games);
      Query<Game> query = factory.create("from Game where description : 'game'");
      QueryResult<Game> result = query.execute();

      // the hit count accuracy does not allow to compute **an exact** hit count
      assertThat(result.count().isExact()).isFalse();

      query = factory.create("from Game where description : 'game'");
      // raise the default accuracy
      query.hitCountAccuracy(5_000);
      result = query.execute();

      assertThat(result.count().isExact()).isTrue();
      assertThat(result.count().value()).isEqualTo(5_000);
   }
}
