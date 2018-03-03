
/*
 * Copyright (c) Medyasoft 2018.
 *
 * All rights reserved.
 */
package org.apache.hadoop.hive.ql.io.orc;

    import static org.apache.hadoop.hive.ql.io.AcidUtils.*;
    import java.io.File;
    import java.io.FileNotFoundException;
    import java.io.FileWriter;
    import java.io.IOException;
    import java.lang.reflect.InvocationTargetException;
    import java.lang.reflect.Method;
    import java.math.BigInteger;
    import java.nio.ByteBuffer;
    import java.nio.file.Files;
    import java.nio.file.Paths;
    import java.util.*;

    import java.util.logging.Level;
    import java.util.logging.Logger;

    import javassist.*;
    import org.apache.hadoop.fs.FileSystem;
    import org.apache.hadoop.fs.Path;
    import org.apache.hadoop.hive.common.type.HiveDecimal;
    import org.apache.hadoop.hive.conf.HiveConf;
    import org.apache.hadoop.hive.metastore.api.Decimal;
    import org.apache.hadoop.hive.metastore.api.hive_metastoreConstants;
    import org.apache.hadoop.hive.ql.io.*;
    import org.apache.hadoop.hive.ql.io.orc.VectorizedOrcAcidRowBatchReader.ColumnizedDeleteEventRegistry;
    import org.apache.hadoop.hive.ql.io.orc.VectorizedOrcAcidRowBatchReader.SortMergedDeleteEventRegistry;
    import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
    import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
    import org.apache.hadoop.mapred.JobConf;
    import org.apache.hadoop.mapred.Reporter;



public class AcidOrc {

    private static final long NUM_ROWID_PER_OTID = 100L;
    //private static final long NUM_OTID = 10L;
    private JobConf gConf;
    private FileSystem gFs;
    private Path gRoot;
    private Path gFilePath;
    private String gRepositoryFile;
    private String gRepositoryPath;
    String gInnerGeneratedClassName;
    Class gAcidClass;
    Object gAcidObject;

    AcidOutputFormat.Options gOptions;

    Map<Integer, org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields> gFieldMaps;

    AcidOrc() throws NotFoundException {
        gFilePath = new Path("/Users/serdar.mutlu/java/");
        gRepositoryPath = "/Users/serdar.mutlu/java/";
        gRepositoryFile = "OrcTables.desc";
        gFieldMaps = new TreeMap<>();
    }

    boolean createTable(String tableName,  Map pFieldMap, int pNumberOfBuckets, boolean pIsAcid) throws IOException {
        String colNames = "";
        //String colTypes = "";
        String colOrcTypes = "";
        int currPos=0;

        gFieldMaps=pFieldMap;

        File file = new File(gRepositoryPath+gRepositoryFile);
        boolean tableExists=false;

        if (!file.exists()) {
            try {
                file.createNewFile();
                System.out.println("Repository file created");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            Scanner s = new Scanner(file);
            while (s.hasNextLine()) {
                //read the file line by line
                String nextLine = s.nextLine();
                //check if the next line contains table definition
                if (nextLine.contains("#" + tableName)) {
                    System.out.println("Table exists!");
                    tableExists = true;
                    break;
                }
            }
        }

        if (!tableExists) {
            // Read column information from map, and set colNames, and colTypes
            org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities ou = new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities();
            for(Map.Entry<Integer, org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields> entry: gFieldMaps.entrySet()) {
                String fname = entry.getValue().getFieldName();
                String fOrctype = entry.getValue().getFieldOrcType();
                String ftype =ou.orcToJava(fOrctype);
                if (currPos==0) {
                    colNames += fname;
                    colOrcTypes += fOrctype;
                }
                else {
                    colNames += "," + fname;
                    colOrcTypes += ":" + fOrctype;
                }
                currPos++;
            }

            // Write Column Name, and Type information to repository file
            FileWriter fr = new FileWriter(file,true);
            fr.append("#"+tableName+"\n");
            fr.append(colNames+"\n");
            fr.append(colOrcTypes+"\n");
            fr.append(pNumberOfBuckets+"\n");
            fr.append(pIsAcid+"\n");
            fr.close();
        }

        if (tableExists)
            return false;
        else
            return true;
    }

    boolean setTable(String tableName) throws NotFoundException, CannotCompileException, IOException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {

        String colNames = "";
        String colOrcTypes = "";
        int buckets=0;
        boolean isAcid=false;
        int currPos=0;

        File file = new File(gRepositoryPath+gRepositoryFile);
        boolean tableExists=false;

        if (!file.exists()) {
            System.out.println("Repository file does not exist!");
            return false;
        }
        else {
            Scanner s = new Scanner(file);
            while (s.hasNextLine()) {
                //read the file line by line
                String nextLine = s.nextLine();
                //check if the next line contains the key word
                if (nextLine.contains("#" + tableName)) {
                    colNames = s.nextLine();
                    colOrcTypes = s.nextLine();
                    buckets = Integer.parseInt(s.nextLine());
                    isAcid = Boolean.parseBoolean(s.nextLine());
                    tableExists = true;
                    break;
                }
            }
        }

        if (!tableExists) {
            System.out.println("Table does not exist!");
            return false;
        }


        if (gFieldMaps.isEmpty()) {
            String[] colNamesArray = colNames.split(",");
            String[] colOrcTypesArray = colOrcTypes.split(":");
            for (int i = 0; i < colNamesArray.length; i++)
                gFieldMaps.put(i, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields(colNamesArray[i], colOrcTypesArray[i]));
        }

        ClassPool pool = ClassPool.getDefault();
        pool.importPackage("org.apache.hadoop.hive.ql.io.orc");
        pool.importPackage("org.apache.hadoop.hive.ql.io.RecordIdentifier");

        gInnerGeneratedClassName = "OrcAcidClass"+System.currentTimeMillis();
        CtClass point = pool.makeClass(gInnerGeneratedClassName);

        String stmt = "RecordIdentifier ROW__ID;";
        CtField f = CtField.make(stmt, point);
        point.addField(f);

        String bodyOfSetDataFields="";

        org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities ou = new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities();
        for(Map.Entry<Integer, org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields> entry: gFieldMaps.entrySet()) {
            String fname = entry.getValue().getFieldName();
            String fOrctype = entry.getValue().getFieldOrcType();
            String ftype = ou.orcToJava(fOrctype);
            stmt = "public " + ftype +" " + fname + "=null;";
            f = CtField.make(stmt, point);
            point.addField(f);

            bodyOfSetDataFields += fname + " = (" + ftype + ") dataRow["+entry.getKey()+"]; ";
        }

        point.addConstructor(CtNewConstructor.make("public " + gInnerGeneratedClassName+"() {" +
                "ROW__ID = null;" +
                "}", point));

        /*point.addConstructor(CtNewConstructor.make("public " + innerGeneratedClassName+"(long origTxn, long rowId, int bucket) {" +
                "ROW__ID = new RecordIdentifier(origTxn, bucket, rowId);" +
                "}", point));*/

        point.addMethod(CtNewMethod.make(
                "public void setRecordID(long origTxn, long rowId, int bucket) {" +
                        "ROW__ID = new RecordIdentifier(origTxn, bucket, rowId);" +
                        "}", point));

        point.addMethod(CtNewMethod.make(
                "public void setDataFields(Object[] dataRow) {" +
                        bodyOfSetDataFields+
                        "}", point));

        point.addMethod(CtNewMethod.make(
                "public String getColumnNamesProperty() {" +
                        "return \""+colNames+"\";" +
                        "}",
                point));

        point.addMethod(CtNewMethod.make(
                "public String getColumnTypesProperty() {" +
                        "return \""+colOrcTypes+"\";" +
                        "}",
                point));

        //Class tmpcls=point.toClass(); //Don't delete !!! Somehow necessary
        gAcidClass = point.toClass();
        gAcidObject = gAcidClass.newInstance();

        //point.writeFile("/tmp/");

        ObjectInspector inspector;
        inspector = ObjectInspectorFactory.getReflectionObjectInspector
                (gAcidClass, ObjectInspectorFactory.ObjectInspectorOptions.JAVA);

        gConf = new JobConf();
        gConf.set("bucket_count", "1");
        gConf.set(hive_metastoreConstants.TABLE_IS_TRANSACTIONAL, "true");
        gConf.setBoolean(HiveConf.ConfVars.HIVE_TRANSACTIONAL_TABLE_SCAN.varname, true);
        gConf.set(hive_metastoreConstants.TABLE_TRANSACTIONAL_PROPERTIES, "default");
        gConf.setInt(HiveConf.ConfVars.HIVE_TXN_OPERATIONAL_PROPERTIES.varname,
                AcidOperationalProperties.getDefault().toInt()); // Default is 1(all ACID), other option is 4(insert only)

        Method colNameMethod = gAcidClass.getDeclaredMethod("getColumnNamesProperty", null);
        Method colTypeMethod = gAcidClass.getDeclaredMethod("getColumnTypesProperty", null);

        gConf.set(IOConstants.SCHEMA_EVOLUTION_COLUMNS, String.valueOf(colNameMethod.invoke(gAcidObject,new Object[]{})));
        gConf.set(IOConstants.SCHEMA_EVOLUTION_COLUMNS_TYPES, String.valueOf(colTypeMethod.invoke(gAcidObject,new Object[]{})));
        gConf.setBoolean(HiveConf.ConfVars.HIVE_VECTORIZATION_ENABLED.varname, true);
        gConf.set(HiveConf.ConfVars.HIVE_ORC_SPLIT_STRATEGY.varname, "BI");
        gFs = FileSystem.getLocal(gConf);

        gRoot = new Path(gFilePath, tableName);
        gFs.delete(gRoot, true);

        int bucket = 0;

        gOptions = new AcidOutputFormat.Options(gConf)
                .filesystem(gFs)
                .bucket(bucket)
                .writingBase(false)
                .inspector(inspector)
                .reporter(Reporter.NULL)
                .recordIdColumn(0)
                .finalDestination(gRoot);

        return true;
    }

    public void  setup(Object[] y1) throws Exception {
        int bucket = 0;

        long currTxnId = 1;
        gOptions.minimumTransactionId(currTxnId).maximumTransactionId(currTxnId);
        RecordUpdater updater = new OrcRecordUpdater(gRoot, gOptions);
        for (long j = 1; j<= NUM_ROWID_PER_OTID; ++j) {
            Class<?>[] argClasses = { long.class, long.class, int.class };
            Method setRecordIDMethod = gAcidClass.getDeclaredMethod("setRecordID", argClasses);
            Object[] argValues = {j,1,bucket};
            setRecordIDMethod.invoke(gAcidObject, argValues);

            Class<?>[] argClasses2 = { Object[].class };
            Method setDataFieldsMethod = gAcidClass.getDeclaredMethod("setDataFields", argClasses2);
            Object[] argValues2 = {y1};
            setDataFieldsMethod.invoke(gAcidObject, argValues2);


            updater.insert(currTxnId, gAcidObject);
        }
        updater.close(false);
/*
        int maxTxnLoop=10;
        for (int txnLoop=2; txnLoop<maxTxnLoop; txnLoop++) {
            currTxnId = txnLoop;
            options.minimumTransactionId(currTxnId).maximumTransactionId(currTxnId);
            updater = new OrcRecordUpdater(root, options);
            for (long j = 0; j < NUM_ROWID_PER_OTID; ++j) {
                if (j % maxTxnLoop == txnLoop) {
                    updater.update(currTxnId, new org.apache.hadoop.hive.ql.io.orc. xOrcAcidClass(j, 1, bucket));
                }
            }
            updater.close(false);
        }

        currTxnId = 15;
        options.minimumTransactionId(currTxnId).maximumTransactionId(currTxnId);
        updater = new OrcRecordUpdater(root, options);
        // Create a single insert delta with 150,000 rows, with 15000 rowIds per original transaction id.
        for (long j = 1; j<= NUM_ROWID_PER_OTID; ++j) {
            updater.insert(currTxnId, new org.apache.hadoop.hive.ql.io.orc. xOrcAcidClass(j, 1, bucket));
        }
        updater.close(false);

        currTxnId = 16;
        options.minimumTransactionId(currTxnId).maximumTransactionId(currTxnId);
        updater = new OrcRecordUpdater(root, options);
        for (long j = 1; j<= NUM_ROWID_PER_OTID; ++j) {
            updater.insert(currTxnId, new org.apache.hadoop.hive.ql.io.orc. xOrcAcidClass(j, 1, bucket));
        }
        updater.close(false);

        currTxnId = 1000;
        options.minimumTransactionId(currTxnId).maximumTransactionId(currTxnId);
        updater = new OrcRecordUpdater(root, options);
        for (long j = 0; j < NUM_ROWID_PER_OTID; ++j) {
            if (j % 7 == 3) {
                updater.delete(currTxnId, new org.apache.hadoop.hive.ql.io.orc. xOrcAcidClass(j, 1, bucket));
            }
        }
        updater.close(false);*/
    }

    public void insertSingleRecord(Long trxID, Long rowID, int bucketID,  Object[] dataRow) throws Exception {
        int bucket = 0;

        gOptions.minimumTransactionId(trxID).maximumTransactionId(trxID);
        RecordUpdater updater = new OrcRecordUpdater(gRoot, gOptions);
        Class<?>[] argClasses = { long.class, long.class, int.class };

        Method setRecordIDMethod = gAcidClass.getDeclaredMethod("setRecordID", argClasses);
        Object[] argValues = {trxID, rowID, bucketID};
        setRecordIDMethod.invoke(gAcidObject, argValues);

        Class<?>[] argClasses2 = { Object[].class };
        Method setDataFieldsMethod = gAcidClass.getDeclaredMethod("setDataFields", argClasses2);
        Object[] argValues2 = {dataRow};
        setDataFieldsMethod.invoke(gAcidObject, argValues2);

        updater.insert(trxID, gAcidObject);
        updater.close(false);
    }

    public void insertMultiRecord(Long trxID, Long rowID, int bucketID,  Object[][] dataRows) throws Exception {
        int bucket = 0;

        gOptions.minimumTransactionId(trxID).maximumTransactionId(trxID);
        RecordUpdater updater = new OrcRecordUpdater(gRoot, gOptions);
        Class<?>[] argClasses = { long.class, long.class, int.class };

        Method setRecordIDMethod = gAcidClass.getDeclaredMethod("setRecordID", argClasses);
        Object[] argValues = {trxID, rowID, bucketID};
        setRecordIDMethod.invoke(gAcidObject, argValues);

        Class<?>[] argClasses2 = { Object[].class };
        Method setDataFieldsMethod = gAcidClass.getDeclaredMethod("setDataFields", argClasses2);
        for (int i=0; i<dataRows.length; i++) {
            Object[] argValues2 = {dataRows[i]};
            setDataFieldsMethod.invoke(gAcidObject, argValues2);
            updater.insert(trxID, gAcidObject);
        }

        updater.close(false);
    }

    public void deleteSingleRecord(Long trxID, Long rowID, int bucketID) throws Exception {
        int bucket = 0;

        gOptions.minimumTransactionId(trxID).maximumTransactionId(trxID);
        RecordUpdater updater = new OrcRecordUpdater(gRoot, gOptions);
        Class<?>[] argClasses = { long.class, long.class, int.class };

        Method setRecordIDMethod = gAcidClass.getDeclaredMethod("setRecordID", argClasses);
        Object[] argValues = {trxID, rowID, bucketID};
        setRecordIDMethod.invoke(gAcidObject, argValues);

        /*Class<?>[] argClasses2 = { Object[].class };
        Method setDataFieldsMethod = gAcidClass.getDeclaredMethod("setDataFields", argClasses2);
        Object[] argValues2 = {dataRow};
        setDataFieldsMethod.invoke(gAcidObject, argValues2);*/

        updater.delete(trxID, gAcidObject);
        updater.close(false);
    }
    private List<OrcSplit> getSplits() throws Exception {

        gConf.setInt(HiveConf.ConfVars.HIVE_TXN_OPERATIONAL_PROPERTIES.varname,
                AcidOperationalProperties.getDefault().toInt());
        OrcInputFormat.Context context = new OrcInputFormat.Context(gConf);
        OrcInputFormat.FileGenerator gen = new OrcInputFormat.FileGenerator(context, gFs, gRoot, false, null);
        OrcInputFormat.AcidDirInfo adi = gen.call();
        List<OrcInputFormat.SplitStrategy<?>> splitStrategies = OrcInputFormat.determineSplitStrategies(
                null, context, adi.fs, adi.splitPath, adi.acidInfo, adi.baseFiles, adi.parsedDeltas,
                null, null, true);
        List<OrcSplit> splits = ((OrcInputFormat.ACIDSplitStrategy)splitStrategies.get(0)).getSplits();
        return splits;
    }

    public void testVectorizedOrcAcidRowBatchReader() throws Exception {
        testVectorizedOrcAcidRowBatchReader(ColumnizedDeleteEventRegistry.class.getName());

        int oldValue = gConf.getInt(HiveConf.ConfVars.HIVE_TRANSACTIONAL_NUM_EVENTS_IN_MEMORY.varname, 1000000);
        gConf.setInt(HiveConf.ConfVars.HIVE_TRANSACTIONAL_NUM_EVENTS_IN_MEMORY.varname, 1000);
        testVectorizedOrcAcidRowBatchReader(SortMergedDeleteEventRegistry.class.getName());
        gConf.setInt(HiveConf.ConfVars.HIVE_TRANSACTIONAL_NUM_EVENTS_IN_MEMORY.varname, oldValue);
    }

    private void testVectorizedOrcAcidRowBatchReader(String deleteEventRegistry) throws Exception {
        List<OrcSplit> splits = getSplits();

        for (int sp = 0; sp < splits.size(); sp++) {
            OrcInputFormat inputFormat = new OrcInputFormat();
            AcidInputFormat.RowReader<OrcStruct> reader = inputFormat.getReader(splits.get(sp),
                    new AcidInputFormat.Options(gConf));

            RecordIdentifier id = reader.createKey();
            OrcStruct struct = reader.createValue();
            int nOfRecs=0;
            while (reader.next(id, struct)) {
                //System.out.println(" TransactionID:" + id.getTransactionId() + " RowID:" + id.getRowId() + " BucketID:" + id.getBucketId() + " " + struct.getFieldValue(0) + " " + struct.getFieldValue(1));
                nOfRecs++;
            }
            //System.out.println("Split No:"+sp+" Number of Records:" + nOfRecs);
        }
    }

    public static void main(String [ ] args) throws Exception {

        AcidOrc x,y;

        Map<Integer, org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields> fieldsx = new TreeMap<>();
        int buckets = 50;
        fieldsx.put(0, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field1","bigint"));
        fieldsx.put(1, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field2","string"));
        fieldsx.put(2, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field3","string"));
        fieldsx.put(3, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field4","double"));
        fieldsx.put(4, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field5","int"));
        fieldsx.put(5, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field6","int"));
        fieldsx.put(6, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field7","string"));
        fieldsx.put(7, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field8","string"));
        fieldsx.put(8, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field9","double"));
        fieldsx.put(9, new org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.dbFields("field10","bigint"));

        x = new AcidOrc();
        x.createTable("Tablex",fieldsx,10,true);
        x.setTable("Tablex");
        byte[][] tuple= new byte[10][];
        for (int i=0; i<10; i++)
            tuple[i] = new byte[100];

        tuple[0] = ByteBuffer.allocate(Long.BYTES).putLong(100000).array();
        tuple[1] = "Hello1".getBytes();
        tuple[2] = "Hello2".getBytes();
        tuple[3] = ByteBuffer.allocate(Double.BYTES).putDouble(12345.67890).array();
        tuple[4] = ByteBuffer.allocate(Integer.BYTES).putInt(123).array();
        tuple[5] = ByteBuffer.allocate(Integer.BYTES).putInt(1234).array();
        tuple[6] = "Hello6".getBytes();
        tuple[7] = "Hello7".getBytes();
        tuple[8] = ByteBuffer.allocate(Double.BYTES).putDouble(12345.67890).array();
        tuple[9] = ByteBuffer.allocate(Long.BYTES).putLong(987654321).array();

        Object[] y1;

        long prev = System.currentTimeMillis();
        y1= org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.tupleColumnTypeSet(x.gFieldMaps, tuple);
        for (int i=0; i<50; i++)
            x.insertSingleRecord(new Long(i), new Long(i),1, y1);
        long now = System.currentTimeMillis();

        System.out.println("Time spent single:"+(now-prev));
        tuple[0] = ByteBuffer.allocate(Long.BYTES).putLong(200000).array();
        tuple[1] = "Hello1".getBytes();
        tuple[2] = "Hello2".getBytes();
        tuple[3] = ByteBuffer.allocate(Double.BYTES).putDouble(22345.67890).array();
        tuple[4] = ByteBuffer.allocate(Integer.BYTES).putInt(223).array();
        tuple[5] = ByteBuffer.allocate(Integer.BYTES).putInt(2234).array();
        tuple[6] = "Hello6".getBytes();
        tuple[7] = "Hello7".getBytes();
        tuple[8] = ByteBuffer.allocate(Double.BYTES).putDouble(22345.67890).array();
        tuple[9] = ByteBuffer.allocate(Long.BYTES).putLong(987654321).array();

        prev = System.currentTimeMillis();
        Object[][] yy1=new Object[50][];
        for (int i=0; i<50; i++)
            yy1[i]= org.apache.hadoop.hive.ql.io.orc.MSOrcUtilities.tupleColumnTypeSet(x.gFieldMaps, tuple);
        x.insertMultiRecord(new Long(999), new Long(0),1, yy1);
        now = System.currentTimeMillis();
        System.out.println("Time spent multi:"+(now-prev));

        System.out.println(x.gOptions.getMaximumTransactionId());

        x.deleteSingleRecord(999L,0L,0);
        //x.setup();
        //System.out.println("Here comes first x");
        //x.testVectorizedOrcAcidRowBatchReader();

/*
        Map<Integer, dbFields> fieldsy = new TreeMap<>();
        fieldsy.put(1, new dbFields("field1","bigint"));
        fieldsy.put(2, new dbFields("field2","string"));
        fieldsy.put(3, new dbFields("field3","string"));
        fieldsy.put(4, new dbFields("field4","double"));
        fieldsy.put(5, new dbFields("field5","int"));

        y = new AcidOrc();
        y.createTable("Tabley",fieldsy,10,true);
        y.setTable("Tabley");
        y.setup();
        System.out.println("Here comes first y");
        y.testVectorizedOrcAcidRowBatchReader();

        System.out.println("Here comes second x");
        x.testVectorizedOrcAcidRowBatchReader();
*/
    }
}

/*
OrigTxn CurrTxn Row     OrigTxn CurrTxn	Row     OrigTxn CurrTxn Row
    1       2   0           5       5   0           6       6   0
    1	    3	1		    5	    5	1		    6	    6	1
    1	    4	2		    5	    5	2		    6	    6	2
    1	    7	3		    5	    5	3		    6	    6	3
    1	    8	4		    5	    5	4		    6	    6	4
    1	    1	5		    5	    5	5		    6	    6	5
    1	    1	6		    5	    5	6		    6	    6	6
    1	    2	7		    5	    5	7		    6	    6	7
    1	    3	8		    5	    5	8		    6	    6	8
    1	    4	9		    5	    5	9		    6	    6	9
 */
