package org.apache.ignite.internal.processors.cache;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.binary.BinaryObjectException;
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

  /** Counter to generate unique type names. */
  private static int typeCnt = 0;

  private static final String KEY_TYPE = "TestKey", VAL_TYPE = "TestValue";

  private static final String TBL_NAME = "TEST_VALUE";

  private static final String DEFAULT_TMPL_NAME = "TMPL_CFG_1";

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

  private String createTypeName(String prefix) {
    return prefix + typeCnt++;
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
    return qryEntity.addQueryField("ID", Long.class.getName(), null)
            .addQueryField("NAME", String.class.getName(), null)
            .addQueryField("AGE", Integer.class.getName(), null)
            .addQueryField("COMPANY", String.class.getName(), null)
            .addQueryField("CITY", String.class.getName(), null);
  }

  private IgniteCache<BinaryObject, BinaryObject> configureCache(String tblName,
                                                                 String keyType, String valueType,
                                                                 Collection<String> keyFields,
                                                                 boolean createMetaOnStart
  ) {
    CacheConfiguration<BinaryObject, BinaryObject>ccfg = new CacheConfiguration<>(DEFAULT_CACHE_NAME);

    ccfg.setSqlSchema("PUBLIC");
    ccfg.setName(tblName);

    Set<String> keys = new LinkedHashSet<>(keyFields);

    QueryEntity qryEntity = new QueryEntity(keyType, valueType);
    fillTestEntityFields(qryEntity.setTableName(tblName).setKeyFields(keys));

    ccfg.setQueryEntities(F.asList(qryEntity));
    ccfg.setKeyConfiguration(new CacheKeyConfiguration(keyType, "CITY"));

    ccfg.setCreateMetaOnStart(createMetaOnStart);

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
    String tblName = createTableName(TBL_NAME);
    String keyType = createTypeName(KEY_TYPE);
    String valType = createTypeName(VAL_TYPE);

    assertThrowsWithCause(
            () -> configureCache(tblName, keyType, valType, Collections.singletonList("id"), false),
            IgniteCheckedException.class
    );
  }

  @Test
  public void testInsertWithComplexKey() {
    String tblName = createTableName(TBL_NAME);
    String keyType = createTypeName(KEY_TYPE);
    String valType = createTypeName(VAL_TYPE);

    configureCache(tblName, keyType, valType, Arrays.asList("ID", "CITY"), true);

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
    String tblName0 = createTableName(TBL_NAME);
    String keyType = createTypeName(KEY_TYPE);
    String valType = createTypeName(VAL_TYPE);

    IgniteCache<BinaryObject, BinaryObject> cache0 = configureCache(tblName0, keyType, valType, Arrays.asList("ID", "CITY"), true);

    String affKeyField = getTableAffinityKeyField(tblName0);
    assertEquals("CITY", affKeyField);

    BinaryObject key = buildKeyStrictly(keyType, 1L);
    BinaryObject val = buildVal(valType);

    cache0.put(key, val);

    executeSql(
            "insert into " + tblName0 + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", "Moscow", 25
    );

    List<List<?>> rs = executeSql("select * from " + tblName0);
    assertEquals(2, rs.size());
  }

  @Test
  public void testPutWithComplexKeySimple() {
    String tblName0 = createTableName(TBL_NAME);
    String keyType = createTypeName(KEY_TYPE);
    String valType = createTypeName(VAL_TYPE);

    IgniteCache<BinaryObject, BinaryObject> cache0 = configureCache(tblName0, keyType, valType, Arrays.asList("ID", "CITY"), true);

    String affKeyField = getTableAffinityKeyField(tblName0);
    assertEquals("CITY", affKeyField);

    BinaryObject key = buildKeySimple(keyType);
    BinaryObject val = buildVal(valType);

    cache0.put(key, val);

    executeSql(
            "insert into " + tblName0 + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            2L, "TestName2", "TestCompany2", "Moscow", 25
    );

    List<List<?>> rs = executeSql("select * from " + tblName0);
    assertEquals(2, rs.size());
  }

  @Test
  public void testInsertWithClassDefined() {
    String tblName = createTableName(TBL_NAME);

    CacheConfiguration ccfg = new CacheConfiguration(DEFAULT_CACHE_NAME);

    ccfg.setSqlSchema("PUBLIC");
    ccfg.setName(tblName);

    Set<String> keys = new LinkedHashSet<>(Arrays.asList("ID", "CITY"));

    QueryEntity qryEntity = new QueryEntity(TestKey.class.getName(), TestValue.class.getName());
    fillTestEntityFields(qryEntity.setTableName(tblName).setKeyFields(keys));

    ccfg.setQueryEntities(F.asList(qryEntity));
    ccfg.setKeyConfiguration(new CacheKeyConfiguration(TestKey.class.getName(), "CITY"));
    ccfg.setCreateMetaOnStart(true);

    node().createCache(ccfg);

    executeSql(
            "insert into " + tblName + " (ID, NAME, COMPANY, CITY, AGE) values(?, ?, ?, ?, ?)",
            1L, "TestName", "TestCompany", "Moscow", 35
    );

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(1, rs.size());
  }

  @Test
  public void testBinaryFieldsUpdate() {
    String tblName = createTableName(TBL_NAME);
    String keyType = createTypeName(KEY_TYPE);
    String valType = createTypeName(VAL_TYPE);

    IgniteCache<BinaryObject, BinaryObject> cache = configureCache(tblName, keyType, valType, Arrays.asList("ID", "CITY"), true);

    assertThrowsWithCause(() -> {
      BinaryObject key1 = buildKeyStrictly(keyType, 1L);
      BinaryObject val1 = node().binary().builder(valType)
              .setField("ID", 1L,  Long.class)
              .setField("CITY", "Moscow", String.class)
              .setField("NAME", 35, Integer.class)
              .build();

      cache.put(key1, val1);
    }, BinaryObjectException.class);
  }

  @Test
  public void testDmlBinaryFieldsUpdate() {
    String tblName = createTableName(TBL_NAME);
    String keyType = createTypeName(KEY_TYPE);
    String valType = createTypeName(VAL_TYPE);

    IgniteCache<BinaryObject, BinaryObject> cache = configureCache(tblName, keyType, valType, Arrays.asList("ID", "CITY"), false);

    BinaryObject key1 = buildKeyStrictly(keyType, 1L);
    BinaryObject val1 = node().binary().builder(valType)
            .setField("ID", 1L, Long.class)
            .setField("CITY", "Moscow", String.class)
            .setField("NAME", 35, Integer.class)
            .build();

    cache.put(key1, val1);

    assertThrowsWithCause(() ->
            executeSql("insert into " + tblName + " (ID, CITY, NAME) values(?, ?, ?)", 2L, "Moscow", "NameTest2"),
            BinaryObjectException.class
    );

    List<List<?>> rs = executeSql("select * from " + tblName);
    assertEquals(1, rs.size());
  }

  @Test
  public void testDdlBinaryFieldsUpdate() {
    String tblName = createTableName(TBL_NAME);
    String keyType = createTypeName(KEY_TYPE);
    String valType = createTypeName(VAL_TYPE);

    CacheConfiguration<BinaryObject, BinaryObject> ccfg = new CacheConfiguration<>(DEFAULT_TMPL_NAME);
    ccfg.setSqlSchema("PUBLIC");
    ccfg.setAffinity(new RendezvousAffinityFunction(false,1024));
    ccfg.setCreateMetaOnStart(true);

    node().addCacheConfiguration(ccfg);

    createTable(tblName, DEFAULT_TMPL_NAME, keyType, valType);

    IgniteCache<BinaryObject, BinaryObject> cache = node().cache(tblName);

    assertThrowsWithCause(() -> {
      BinaryObject key1 = buildKeyStrictly(keyType, 1L);
      BinaryObject val1 = node().binary().builder(valType)
              .setField("ID", 1L, Long.class)
              .setField("CITY", "Moscow", String.class)
              .setField("NAME", 35, Integer.class)
              .build();

      cache.put(key1, val1);
    }, BinaryObjectException.class);
  }

  /**
   * Creates table based on a template.
   */
  private void createTable(String tableName, String template, String keyType, String valType) {
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
            tableName, template, keyType, valType);

    cache.query(new SqlFieldsQuery(sql).setSchema("PUBLIC"));
  }

  private BinaryObject buildKeyStrictly(String keyType, Long id) {
    BinaryObjectBuilder builder = node().binary().builder(keyType);
    builder.affinityFieldName("CITY");
    builder.setField("ID", id, Long.class);
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

  private BinaryObject buildKeySimple(String keyType) {
    BinaryObjectBuilder builder = node().binary().builder(keyType);
    builder.setField("ID", 1L,  Long.class);
    builder.setField("CITY", "Moscow", String.class);
    return builder.build();
  }

  private BinaryObject buildVal(String valType) {
    BinaryObjectBuilder builder = node().binary().builder(valType);
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
