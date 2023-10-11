package org.apache.ignite.internal.processors.cache;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.cache.CacheKeyConfiguration;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.binary.builder.BinaryObjectBuilderImpl;
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

/**
* TODO:
 * 1. Remove CITY field from keys
 * 2. Create cache with DDL
 * 3. Create builder without type definition
*/
public class IgniteCacheInsertSqlQueryAffinityKeyTest extends GridCommonAbstractTest {

  /** Counter to generate unique table names. */
  private static int tblCnt = 0;

  private static String KEY_TYPE = "TestKey", VAL_TYPE = "TestValue";

  protected static final String DEFAULT_TMPL_NAME = "TMPL_CFG_1";

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

  private IgniteCache<BinaryObject, BinaryObject> configureCache(String tblName, Collection<String> keyFields) {
    CacheConfiguration<BinaryObject, BinaryObject>ccfg = new CacheConfiguration<>(DEFAULT_CACHE_NAME);

    ccfg.setSqlSchema("PUBLIC");
    ccfg.setName(tblName);

    Set<String> keys = new LinkedHashSet<>(keyFields);

    QueryEntity qryEntity = new QueryEntity(KEY_TYPE, VAL_TYPE);
    fillTestEntityFields(qryEntity.setTableName(tblName).setKeyFields(keys));

    ccfg.setQueryEntities(F.asList(qryEntity));
    ccfg.setKeyConfiguration(new CacheKeyConfiguration(KEY_TYPE, "city"));

    IgniteEx ignite = node();
    return ignite.createCache(ccfg);
  }

  private String getTableAffinityKeyField(String tblName) {
    String sql = "select AFFINITY_KEY_COLUMN from SYS.TABLES where TABLE_NAME = '" + tblName + "'";
    List<List<?>> tableMeta = executeSql(sql);
    return !tableMeta.isEmpty() ? (String) tableMeta.get(0).get(0) : null;
  }

  //@Test
  public void testCreateCacheWithKeyTruncated() {
    String tblName = createTableName("TEST_VALUE");
    assertThrowsWithCause(
            () -> configureCache(tblName, Collections.singletonList("id")),
            IgniteCheckedException.class
    );
  }

  @Test
  public void testInsertWithComplexKey() {
    String tblName = createTableName("TEST_VALUE");

    configureCache(tblName, Arrays.asList("id", "city"));

    String affKeyField = getTableAffinityKeyField(tblName);
    assertEquals("CITY", affKeyField);

    executeSql(
            "insert into " + tblName + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            1L, "TestName", "TestCompany", "Moscow", 35 // TODO: проверить что в той партиции
    );

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(1, rs.size());
  }

  @Test
  public void testPutWithComplexKeyStrictly() {
    String tblName0 = createTableName("TEST_VALUE");

    IgniteCache<BinaryObject, BinaryObject> cache0 = configureCache(tblName0, Arrays.asList("id", "city"));

    String affKeyField = getTableAffinityKeyField(tblName0);
    assertEquals("CITY", affKeyField);

    BinaryObject key = buildKeyStrictly(1L);
    BinaryObject val = buildVal();

    cache0.put(key, val);

    executeSql(
            "insert into " + tblName0 + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", "Moscow", 25
    );

    List<List<?>> rs = executeSql("select * from " + tblName0);
    assertEquals(2, rs.size());

    /*String tblNameTrunc = createTableName("TEST_VALUE");

    IgniteCache<BinaryObject, BinaryObject> cacheTrunc = configureCache(tblNameTrunc, Collections.singletonList("id"));

    affKeyField = getTableAffinityKeyField(tblNameTrunc);
    assertNull(affKeyField);

    try (IgniteDataStreamer<BinaryObject, BinaryObject> streamer = createDataStreamer(node(), cacheTrunc.getName())) {
      BinaryObject key = buildKeyStrictly();
      BinaryObject val = buildVal();

      streamer.addData(key, val);
      streamer.flush();
    }

    executeSql(
            "insert into " + tblNameTrunc + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", "Moscow", 25
    );

    rs = executeSql("select * from " + tblNameTrunc);
    assertEquals(2, rs.size());*/
  }

  @Test
  public void testPutWithComplexKeySimple() {
    String tblName0 = createTableName("TEST_VALUE");

    IgniteCache<BinaryObject, BinaryObject> cache0 = configureCache(tblName0, Arrays.asList("id", "city"));

    String affKeyField = getTableAffinityKeyField(tblName0);
    assertEquals("CITY", affKeyField);

    BinaryObject key = buildKeySimple();
    BinaryObject val = buildVal();

    cache0.put(key, val);

    executeSql(
            "insert into " + tblName0 + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", "Moscow", 25
    );

    List<List<?>> rs = executeSql("select * from " + tblName0);
    assertEquals(2, rs.size());

    /*String tblNameTrunc = createTableName("TEST_VALUE");

    IgniteCache<BinaryObject, BinaryObject> cacheTrunc = configureCache(tblNameTrunc, Collections.singletonList("id"));

    affKeyField = getTableAffinityKeyField(tblNameTrunc);
    assertNull(affKeyField);

    BinaryObject keyTrunc = buildKeySimple();
    BinaryObject val1 = buildVal();

    cacheTrunc.put(keyTrunc, val1);

    executeSql(
            "insert into " + tblNameTrunc + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", "Moscow", 25
    );

    rs = executeSql("select * from " + tblNameTrunc);
    assertEquals(2, rs.size());*/
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
  public void testInsertWithClassDefined() {
    String tblName = createTableName("TEST_VALUE");

    CacheConfiguration ccfg = new CacheConfiguration(DEFAULT_CACHE_NAME);

    ccfg.setSqlSchema("PUBLIC");
    ccfg.setName(tblName);

    Set<String> keys = U.newLinkedHashSet(2);
    keys.add("id");
    keys.add("city");

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

  @Test
  public void testBinaryFieldsUpdate() {
    String tblName = createTableName("TEST_VALUE");
    IgniteCache<BinaryObject, BinaryObject> cache = configureCache(tblName, Arrays.asList("id", "city"));

    BinaryObject key1 = buildKeyStrictly(1L);
    BinaryObject val1 = node().binary().builder(VAL_TYPE)
            .setField("ID", 1L,  Long.class)
            .setField("CITY", "Moscow", String.class)
            .setField("NAME", 35, Integer.class)
            .build();

    cache.put(key1, val1); // TODO: ошибка должна быть тут

    BinaryObject key2 = buildKeyStrictly(2L);
    BinaryObject val2 = node().binary().builder(VAL_TYPE)
            .setField("ID", 2L,  Long.class)
            .setField("CITY", "Moscow", String.class)
            .setField("NAME", "TestName1", String.class)
            .build();

    cache.put(key2, val2);

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(2, rs.size());
  }

  @Test
  public void testDmlBinaryFieldsUpdate() {
    String tblName = createTableName("TEST_VALUE");
    IgniteCache<BinaryObject, BinaryObject> cache = configureCache(tblName, Arrays.asList("id", "city"));

    BinaryObject key1 = buildKeyStrictly(1L);
    BinaryObject val1 = node().binary().builder(VAL_TYPE)
            .setField("id", 1L,  Long.class)
            .setField("city", "Moscow", String.class)
            .setField("name", 35, Integer.class)
            .build();

    cache.put(key1, val1); // TODO: ошибка должна быть тут

    executeSql("insert into " + tblName + " (ID, CITY, NAME) values(?, ?, ?)", 2L, "Moscow", "NameTest2");

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(2, rs.size());
  }

  @Test
  public void testDdlBinaryFieldsUpdate() {
    String tblName = createTableName("TEST_VALUE");

    CacheConfiguration<BinaryObject, BinaryObject> ccfg = new CacheConfiguration<>(DEFAULT_TMPL_NAME);
    ccfg.setSqlSchema("PUBLIC");
    ccfg.setAffinity(new RendezvousAffinityFunction(false,1024));

    node().addCacheConfiguration(ccfg);

    createTable(tblName, DEFAULT_TMPL_NAME);

    IgniteCache<BinaryObject, BinaryObject> cache = node().cache(tblName);

    BinaryObject key1 = buildKeyStrictly(1L);
    BinaryObject val1 = node().binary().builder(VAL_TYPE)
            .setField("ID", 1L,  Long.class)
            .setField("CITY", "Moscow", String.class)
            .setField("NAME", 35, Integer.class)
            .build();

    cache.put(key1, val1); // TODO: ошибка должна быть тут

    BinaryObject key2 = buildKeyStrictly(2L);
    BinaryObject val2 = node().binary().builder(VAL_TYPE)
            .setField("ID", 2L,  Long.class)
            .setField("CITY", "Moscow", String.class)
            .setField("NAME", "TestName1", String.class)
            .build();

    cache.put(key2, val2);
  }

  /**
   * Creates table based on a template.
   */
  private void createTable(String tableName, String template) {
    IgniteCache<?,?> cache = node().getOrCreateCache("test");

    String sql = String.format(
            "CREATE TABLE IF NOT EXISTS %1$s(\n" +
                    "  ID      LONG NOT NULL,\n" +
                    "  CITY    VARCHAR NOT NULL,\n" +
                    "  NAME    VARCHAR,\n" +
                    "  COMPANY VARCHAR,\n" +
                    "  AGE     INT,\n" +
                    "  PRIMARY KEY (ID, CITY)\n" +
                    ") with \"TEMPLATE=%2$s,AFFINITY_KEY=CITY,ATOMICITY=TRANSACTIONAL,CACHE_NAME=%1$s,KEY_TYPE=%3$s,VALUE_TYPE=%4$s\";",
            tableName, template, KEY_TYPE, VAL_TYPE);

    cache.query(new SqlFieldsQuery(sql).setSchema("PUBLIC"));
  }

  private BinaryObject buildKeyStrictly(Long id) {
    BinaryObjectBuilder builder = node().binary().builder(KEY_TYPE);
    builder.affinityFieldName("city");
    builder.setField("ID", id,  Long.class);
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
