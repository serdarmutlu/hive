package org.apache.hadoop.hive.ql.io.orc;

/*
 * Copyright (c) Medyasoft 2018.
 *
 * All rights reserved.
 */

import com.google.common.collect.Maps;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class MSOrcUtilities {
    static class dbFields {
        private String fieldName;
        private String fieldOrcType;

        dbFields(String fName, String fOrcType) {
            setFieldName(fName);
            setFieldOrcType(fOrcType);
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldOrcType() {
            return fieldOrcType;
        }

        public void setFieldOrcType(String fieldOrcType) {
            this.fieldOrcType = fieldOrcType;
        }

    };

    public static String orcToJava(String orcType) {
        String javaType="";
        switch (orcType) {
            case "array":
                javaType = "String";
                break;
            case "binary":
            case "char":
            case "string":
            case "varchar":
                javaType = "String";
                break;
            case "bigint":
            case "boolean":
            case "date":
            case "smallint":
            case "tinyint":
                javaType = "Long";
                break;
            case "int":
                javaType = "Integer";
                break;
            case "decimal":
                javaType = "HiveDecimal";
                break;
            case "double":
            case "float":
                javaType = "Double";
                break;
            case "map":
                break;
            case "struct":
                break;
            case "timestamp":
                break;
            case "uniontype":
                break;
            default:
        }
        return javaType;
    }

    public static Object[] tupleColumnTypeSet(Map<Integer, dbFields> fieldMaps, byte[][] tuple) {

        Map<Integer, dbFields> tmpMap = new TreeMap<>();

        int tupleSize = tuple.length;
        Object[] columnizedTuple=new Object[tupleSize];
        ByteBuffer wrap;

        tmpMap = fieldMaps;

        for (Integer i=0; i<tupleSize; i++){
            String x=fieldMaps.get(i).getFieldOrcType();
            switch (fieldMaps.get(i).getFieldOrcType()) {
                case "array":
                    //tuple[i] = (String) tuple[i];
                    break;
                case "binary":
                case "char":
                case "string":
                case "varchar":
                    columnizedTuple[i] = new String(tuple[i]);
                    break;
                case "bigint":
                    columnizedTuple[i] = ByteBuffer.wrap(tuple[i]).getLong();
                    break;
                case "boolean":
                case "date":
                case "int":
                case "smallint":
                case "tinyint":
                    columnizedTuple[i] = ByteBuffer.wrap(tuple[i]).getInt();
                    break;
                case "decimal":
                    //x[i] = Decimal. parse Integer.parseInt(new String(tuple[i]));
                    break;
                case "double":
                    columnizedTuple[i] = ByteBuffer.wrap(tuple[i]).getDouble();
                    break;
                case "float":
                    columnizedTuple[i] = ByteBuffer.wrap(tuple[i]).getFloat();
                    break;
                case "map":
                    break;
                case "struct":
                    break;
                case "timestamp":
                    break;
                case "uniontype":
                    break;
                default:
            }
        }
        return columnizedTuple;
    }
}
