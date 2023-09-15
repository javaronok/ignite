package org.apache.ignite.internal.processors.cache;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.cache.CacheKeyConfiguration;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.binary.builder.BinaryObjectBuilderImpl;
import org.apache.ignite.internal.processors.datastreamer.DataStreamerCacheUpdaters;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class IgniteCacheInsertSqlQueryAffinityKeyTest extends GridCommonAbstractTest {

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
   * @param args arguments of statements
   * @return Run result.
   */
  private List<List<?>> executeSql(String stmt, Object... args) {
    return executeSql(node(), stmt, args);
  }

  /**
   * Run SQL statement on specified node.
   *
   * @param node node to execute query.
   * @param stmt Statement to run.
   * @param args arguments of statements
   * @return Run result.
   */
  private List<List<?>> executeSql(IgniteEx node, String stmt, Object... args) {
    return node.context().query().querySqlFields(new SqlFieldsQuery(stmt).setArgs(args), true).getAll();
  }
  private QueryEntity fillTestEntityFields(QueryEntity qryEntity) {
    return qryEntity.addQueryField("id", Long.class.getName(), null)
            .addQueryField("name", String.class.getName(), null)
            .addQueryField("age", Integer.class.getName(), null)
            .addQueryField("company", String.class.getName(), null)
            .addQueryField("city", String.class.getName(), null);
  }

  private IgniteCache<BinaryObject, BinaryObject> configureCache(String tblName) {
    CacheConfiguration<BinaryObject, BinaryObject>ccfg = new CacheConfiguration<>(DEFAULT_CACHE_NAME);

    ccfg.setSqlSchema("PUBLIC");
    ccfg.setName(tblName);

    Set<String> keys = U.newLinkedHashSet(2);
    keys.add("id");
    keys.add("city"); // TODO: Проверить распределение без указания поля

    QueryEntity qryEntity = new QueryEntity(KEY_TYPE, VAL_TYPE);
    fillTestEntityFields(qryEntity.setTableName(tblName).setKeyFields(keys));

    ccfg.setQueryEntities(F.asList(qryEntity));
    ccfg.setKeyConfiguration(new CacheKeyConfiguration(KEY_TYPE, "city"));

    IgniteEx ignite = node();
    return ignite.createCache(ccfg);
  }

  @Test
  public void testInsertWithComplexKey() {
    String tblName = createTableName("TEST_VALUE");

    configureCache(tblName);

    executeSql(
            "insert into " + tblName + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            1L, "TestName", "TestCompany", "Moscow", 35 // TODO: проверить что в той партиции
    );

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(1, rs.size());
  }

  @Test
  public void testStreamWithComplexKeyStrictly() {
    String tblName = createTableName("TEST_VALUE");

    IgniteCache<BinaryObject, BinaryObject> cache = configureCache(tblName);

    try (IgniteDataStreamer<BinaryObject, BinaryObject> streamer = createDataStreamer(node(), cache.getName())) {
      BinaryObject key = buildKeyStrictly();
      BinaryObject val = buildVal();

      streamer.addData(key, val);
      streamer.flush();
    }

    executeSql(
            "insert into " + tblName + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", "Moscow", 25
    );

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(2, rs.size());
  }

  /*@Test
  public void testStreamWithComplexKeyIgnoreCase() {
    String tblName = createTableName("TEST_VALUE");

    IgniteCache<BinaryObject, BinaryObject> cache = configureCache(tblName);

    try (IgniteDataStreamer<BinaryObject, BinaryObject> streamer = createDataStreamer(node(), cache.getName())) {
      BinaryObject key = buildKeyIgnoreCase();
      BinaryObject val = buildVal();

      streamer.addData(key, val);
      streamer.flush();
    }

    executeSql(
            "insert into " + tblName + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", "Moscow", 25
    );

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(2, rs.size());
  }

  @Test
  public void testStreamWithComplexKeySimple() {
    String tblName = createTableName("TEST_VALUE");

    IgniteCache<BinaryObject, BinaryObject> cache = configureCache(tblName);

    try (IgniteDataStreamer<BinaryObject, BinaryObject> streamer = createDataStreamer(node(), cache.getName())) {
      BinaryObject key = buildKeySimple();
      BinaryObject val = buildVal();

      streamer.addData(key, val);
      streamer.flush();
    }

    executeSql(
            "insert into " + tblName + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", "Moscow", 25
    );

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(2, rs.size());
  }

  @Test
  public void testInsertWithClassDefined() {
    String tblName = createTableName("TEST_VALUE");

    CacheConfiguration ccfg = new CacheConfiguration(DEFAULT_CACHE_NAME);

    ccfg.setSqlSchema("PUBLIC");
    ccfg.setName(tblName);

    Set<String> keys = U.newLinkedHashSet(2);
    keys.add("id");

    QueryEntity qryEntity = new QueryEntity(TestKey.class.getName(), TestValue.class.getName());
    fillTestEntityFields(qryEntity.setTableName(tblName).setKeyFields(keys));

    ccfg.setQueryEntities(F.asList(qryEntity));
    ccfg.setKeyConfiguration(new CacheKeyConfiguration(TestKey.class.getName(), "city"));

    node().createCache(ccfg);

    executeSql(
            "insert into " + tblName + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            1L, "TestName", "TestCompany", "Moscow", 35
    );

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(1, rs.size());
  }*/

  private IgniteDataStreamer<BinaryObject, BinaryObject> createDataStreamer(IgniteEx ignite, String cacheName) {
    IgniteDataStreamer<BinaryObject, BinaryObject> streamer = ignite.dataStreamer(cacheName);
    streamer.keepBinary(true);
    streamer.allowOverwrite(true);
    streamer.receiver(DataStreamerCacheUpdaters.batched());
    return streamer;
  }

  private BinaryObject buildKeyStrictly() {
    BinaryObjectBuilderImpl builder = (BinaryObjectBuilderImpl) node().binary().builder(KEY_TYPE);
    builder.affinityFieldName("city");
    builder.setField("ID", 1L,  Long.class);
    builder.setField("CITY", "Moscow", String.class);
    return builder.build();
  }

  private BinaryObject buildKeyIgnoreCase() {
    BinaryObjectBuilderImpl builder = (BinaryObjectBuilderImpl) node().binary().builder(KEY_TYPE);
    builder.affinityFieldName("CITY");
    builder.setField("ID", 1L,  Long.class);
    builder.setField("CITY", "Moscow", String.class);
    return builder.build();
  }

  private BinaryObject buildKeySimple() {
    BinaryObjectBuilder builder = node().binary().builder(KEY_TYPE);
    builder.setField("ID", 1L,  Long.class);
    builder.setField("CITY", "Moscow", String.class);
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

  static class TestKey {
    /** */
    @QuerySqlField
    private long id;

    /**
     * @param id ID.
     */
    public TestKey(int id) {
      this.id = id;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
      if (this == o)
        return true;

      if (o == null || getClass() != o.getClass())
        return false;

      TestKey testKey = (TestKey)o;

      return id == testKey.id;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
      return (int)id;
    }
  }

  static class TestValue {
    /** */
    @QuerySqlField()
    private String name;

    /** */
    @QuerySqlField()
    private String company;

    /** */
    @QuerySqlField()
    private String city;

    /** */
    @QuerySqlField()
    private int age;

    /**
     * @param age Age.
     * @param name Name.
     * @param company Company.
     * @param city City.
     */
    public TestValue(int age, String name, String company, String city) {
      this.age = age;
      this.name = name;
      this.company = company;
      this.city = city;
    }
  }

}
