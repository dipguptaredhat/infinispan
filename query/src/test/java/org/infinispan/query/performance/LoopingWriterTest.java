package org.infinispan.query.performance;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.commons.util.Util;
import org.infinispan.context.Flag;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.query.test.Person;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Starts writing a lot of data in a loop. Used to measure ingestion rate.
 *
 * -server -XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode -XX:+UseParNewGC -Xss500k -Xmx12G -Xms12G -Dlog4j.configurationFile=log4j2.xml
 *
 * @author Sanne Grinovero
 * @since 5.3
 */
@Test(groups = "profiling", testName = "query.performance.LoopingWriterTest")
public class LoopingWriterTest extends AbstractInfinispanTest {

   private static final int TOTAL_LOOPS = Integer.MAX_VALUE;
   private static final int TIMESAMPLE_PERIODICITY = 6000;
   private static final int QUERY_PERIODICITY = 15170;

   public void neverEndingWrite() throws IOException {
      EmbeddedCacheManager embeddedCacheManager = TestCacheManagerFactory.fromXml("nrt-performance-writer-infinispandirectory.xml");
      try {
         Cache<Object, Object> cache = embeddedCacheManager.getCache("Indexed");
         cache = cache.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES);
         writeStuff(cache);
      } finally {
         embeddedCacheManager.stop();
      }
   }

   /**
    * We write a lot of elements.. and run a Query occasionally to check.
    */
   private void writeStuff(final Cache<Object, Object> cache) {
      final long startTime = System.nanoTime();
      for (int i = 1; i < TOTAL_LOOPS; i++) {
         final String key = "K" + i;
         final Person value = new Person(key, key, i);
         cache.put(key, value);
         if (i % QUERY_PERIODICITY == 0) {
            countElementsViaQuery(cache, i);
         }
         if (i % TIMESAMPLE_PERIODICITY == 0) {
            final long currentTimeStamp = System.nanoTime();
            final long elapsed = currentTimeStamp - startTime;
            final double elementsWrittenPerSecond = ((double) TimeUnit.NANOSECONDS.convert(i, TimeUnit.SECONDS)) / elapsed;
            NumberFormat nf = NumberFormat.getNumberInstance();
            nf.setMaximumFractionDigits(2);
            nf.setGroupingUsed(true);
            System.out.println(
                  "Transactions committed to index per second: " + nf.format(elementsWrittenPerSecond) +
                        ". Total documents: " + i +
                        " Total time: " + Util.prettyPrintTime(elapsed, TimeUnit.NANOSECONDS)
            );
         }
      }
   }

   private void countElementsViaQuery(Cache<Object, Object> cache, int expectedElements) {
      QueryFactory queryFactory = Search.getQueryFactory(cache);
      long resultSize = queryFactory.create("FROM " + Person.class.getName()).execute().count().value();
      Assert.assertEquals(resultSize, expectedElements);
      System.out.println("Query OK! found (as expected) " + resultSize + " elements");
   }

   @Test(enabled = false) // Disable explicitly to avoid TestNG thinking this is a test!!
   public static void main(String[] args) throws IOException {
      LoopingWriterTest runner = new LoopingWriterTest();
      runner.neverEndingWrite();
   }

}
