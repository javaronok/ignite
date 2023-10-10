package org.apache.ignite.internal.processors.cache;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.cache.CacheKeyConfiguration;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.apache.ignite.testframework.GridTestUtils.assertThrowsWithCause;

public class IgniteCacheInsertSqlQueryComplexKeyTest extends GridCommonAbstractTest {

  /** Counter to generate unique table names. */
  private static int tblCnt = 0;

  private static String KEY_TYPE = "TestKey", VAL_TYPE = "TestValue";

  /** {@inheritDoc} */
  @Override protected void beforeTestsStarted() throws Exception {
    super.beforeTestsStarted();

    startGrid(0);
  }

  /** {@inheritDoc} */
  @Override protected void afterTestsStopped() throws Exception {
    stopAllGrids();

    super.afterTestsStopped();
  }

  private String createTableName(String prefix) {
    return prefix + "_" + tblCnt++;
  }

  private IgniteEx node() {
    return grid(0);
  }

  /**
   * Run SQL statement on default node.
   *
   * @param stmt Statement to run.
   * @param args Arguments of statements
   * @return Run result.
   */
  private List<List<?>> executeSql(String stmt, Object... args) {
    return executeSql(node(), stmt, null, args);
  }

  /**
   * Run SQL statement on default node and specified partition.
   * Partition may be null.
   *
   * @param stmt Statement to run.
   * @param part Partition number.
   * @param args Arguments of statements
   * @return Run result.
   */
  private List<List<?>> executeSql(String stmt, Integer part, Object... args) {
    return executeSql(node(), stmt, part, args);
  }

  /**
   * Run SQL statement on specified node and specified partition.
   * Partition may be null.
   *
   * @param node node to execute query.
   * @param stmt Statement to run.
   * @param part Partition number.
   * @param args Arguments of statements
   * @return Run result.
   */
  private List<List<?>> executeSql(IgniteEx node, String stmt, Integer part, Object... args) {
    SqlFieldsQuery q = new SqlFieldsQuery(stmt).setArgs(args);

    if (part != null)
      q.setPartitions(part);

    return node.context().query().querySqlFields(q, true).getAll();
  }

  private QueryEntity fillTestEntityFields(QueryEntity qryEntity) {
    return qryEntity.addQueryField("id", Long.class.getName(), null)
            .addQueryField("name", String.class.getName(), null)
            .addQueryField("age", Integer.class.getName(), null)
            .addQueryField("company", String.class.getName(), null)
            .addQueryField("city", String.class.getName(), null);
  }

  private IgniteCache<BinaryObject, BinaryObject> configureCache(String tblName, Collection<String> keyFields) {
    CacheConfiguration<BinaryObject, BinaryObject>ccfg = new CacheConfiguration<>(DEFAULT_CACHE_NAME);

    ccfg.setSqlSchema("PUBLIC");
    ccfg.setName(tblName);
    ccfg.setAffinity(new RendezvousAffinityFunction(false,1024));

    Set<String> keys = new LinkedHashSet<>(keyFields);

    QueryEntity qryEntity = new QueryEntity(KEY_TYPE, VAL_TYPE);
    fillTestEntityFields(qryEntity.setTableName(tblName).setKeyFields(keys));

    ccfg.setQueryEntities(F.asList(qryEntity));
    ccfg.setKeyConfiguration(new CacheKeyConfiguration(KEY_TYPE, "city"));

    IgniteEx ignite = node();
    return ignite.createCache(ccfg);
  }

  @Test
  public void testCreateCacheWithKeyTruncated() {
    String tblName = createTableName("TEST_VALUE");
    assertThrowsWithCause(
            () -> configureCache(tblName, Collections.singletonList("id")),
            IgniteCheckedException.class
    );
  }

  private String getTableAffinityKeyField(String tblName) {
    String sql = "select AFFINITY_KEY_COLUMN from SYS.TABLES where TABLE_NAME = '" + tblName + "'";
    List<List<?>> tableMeta = executeSql(sql);
    return !tableMeta.isEmpty() ? (String) tableMeta.get(0).get(0) : null;
  }

  @Test
  public void testInsertWithComplexKey() {
    String tblName = createTableName("TEST_VALUE");

    configureCache(tblName, Arrays.asList("id", "city"));

    String affKeyField = getTableAffinityKeyField(tblName);
    assertEquals("CITY", affKeyField);

    String affKey = "Moscow";

    executeSql(
            "insert into " + tblName + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            1L, "TestName", "TestCompany", affKey, 35
    );

    int part = node().affinity(tblName).partition(affKey);

    List<List<?>> rs = executeSql("select * from " + tblName, part);
    assertEquals(1, rs.size());
  }

  @Test
  public void testStreamWithComplexKeyStrictly() {
    String tblName = createTableName("TEST_VALUE");

    IgniteCache<BinaryObject, BinaryObject> cache = configureCache(tblName, Arrays.asList("id", "city"));

    String affKeyField = getTableAffinityKeyField(tblName);
    assertEquals("CITY", affKeyField);

    String affKey = "Moscow";

    BinaryObject key = buildKeyStrictly(affKey);
    BinaryObject val = buildVal();
    cache.put(key, val);

    executeSql(
            "insert into " + tblName + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", affKey, 25
    );

    int part = node().affinity(tblName).partition(affKey);

    List<List<?>> rs = executeSql("select * from " + tblName, part);
    assertEquals(2, rs.size());
  }

  private BinaryObject buildKeyStrictly(String affKey) {
    BinaryObjectBuilder builder = node().binary().builder(KEY_TYPE);
    builder.affinityFieldName("CITY");
    builder.setField("ID", 1L,  Long.class);
    builder.setField("CITY", affKey, String.class);
    return builder.build();
  }

  private BinaryObject buildVal() {
    BinaryObjectBuilder builder = node().binary().builder(VAL_TYPE);
    builder.setField("ID", 1L,  Long.class);
    builder.setField("CITY", "Moscow", String.class);
    builder.setField("NAME", "TestName1", String.class);
    builder.setField("COMPANY", "TestCompany1", String.class);
    builder.setField("AGE", 35, Integer.class);
    return builder.build();
  }
}
