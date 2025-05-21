package com.example.blueteeth;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DataDBHelper extends SQLiteOpenHelper {
    private static final String TAG = "DataDBHelper";
    
    // 数据库信息
    private static final String DATABASE_NAME = "data_points.db";
    private static final int DATABASE_VERSION = 1;

    // 表名
    public static final String TABLE_DATA_POINTS = "data_points";

    // 列名
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_DATETIME = "datetime"; // 存储完整日期时间
    public static final String COLUMN_VALUE = "value";
    public static final String COLUMN_TYPE = "type";

    // 创建表SQL语句
    private static final String CREATE_TABLE_DATA_POINTS = "CREATE TABLE " + TABLE_DATA_POINTS + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_TIMESTAMP + " TEXT NOT NULL, "
            + COLUMN_DATETIME + " TEXT NOT NULL, "
            + COLUMN_VALUE + " REAL NOT NULL, "
            + COLUMN_TYPE + " INTEGER NOT NULL);";

    // 单例模式
    private static DataDBHelper instance;

    // 格式化日期
    private SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public static synchronized DataDBHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DataDBHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DataDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_DATA_POINTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 升级数据库的逻辑，目前简单处理：删除旧表，创建新表
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DATA_POINTS);
        onCreate(db);
    }

    /**
     * 添加一个数据点到数据库
     * @param dataPoint 要添加的数据点
     * @return 是否添加成功
     */
    public boolean addDataPoint(DataPoint dataPoint) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        try {
            // 当前日期时间
            String currentDateTime = datetimeFormat.format(new Date());

            values.put(COLUMN_TIMESTAMP, dataPoint.getTimestamp());
            values.put(COLUMN_DATETIME, currentDateTime);
            values.put(COLUMN_VALUE, dataPoint.getValue());
            values.put(COLUMN_TYPE, dataPoint.getType());

            long result = db.insert(TABLE_DATA_POINTS, null, values);
            return result != -1;
        } catch (Exception e) {
            Log.e(TAG, "添加数据点失败: " + e.getMessage());
            return false;
        } finally {
            db.close();
        }
    }

    /**
     * 获取最近一周的数据点
     * @return 数据点列表
     */
    public ArrayList<DataPoint> getLastWeekData() {
        ArrayList<DataPoint> dataPoints = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        try {
            // 计算一周前的日期
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, -7);
            Date oneWeekAgo = calendar.getTime();
            String oneWeekAgoStr = datetimeFormat.format(oneWeekAgo);

            // 查询语句
            String selection = COLUMN_DATETIME + " >= ?";
            String[] selectionArgs = {oneWeekAgoStr};
            String orderBy = COLUMN_DATETIME + " DESC";

            Cursor cursor = db.query(
                    TABLE_DATA_POINTS,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    orderBy
            );

            if (cursor.moveToFirst()) {
                do {
                    int idIndex = cursor.getColumnIndex(COLUMN_ID);
                    int timestampIndex = cursor.getColumnIndex(COLUMN_TIMESTAMP);
                    int valueIndex = cursor.getColumnIndex(COLUMN_VALUE);
                    int typeIndex = cursor.getColumnIndex(COLUMN_TYPE);

                    if (idIndex < 0 || timestampIndex < 0 || valueIndex < 0 || typeIndex < 0) {
                        Log.e(TAG, "列名不存在");
                        continue;
                    }

                    int id = cursor.getInt(idIndex);
                    String timestamp = cursor.getString(timestampIndex);
                    float value = cursor.getFloat(valueIndex);
                    int type = cursor.getInt(typeIndex);

                    DataPoint dataPoint = new DataPoint(timestamp, value, type);
                    dataPoints.add(dataPoint);
                } while (cursor.moveToNext());
            }
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "获取数据失败: " + e.getMessage());
        } finally {
            db.close();
        }

        return dataPoints;
    }

    /**
     * 清除所有数据
     */
    public void clearAllData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_DATA_POINTS, null, null);
        db.close();
    }

    /**
     * 删除一周以前的数据
     * @return 删除的行数
     */
    public int deleteOldData() {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            // 计算一周前的日期
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, -7);
            Date oneWeekAgo = calendar.getTime();
            String oneWeekAgoStr = datetimeFormat.format(oneWeekAgo);

            // 删除一周前的数据
            String whereClause = COLUMN_DATETIME + " < ?";
            String[] whereArgs = {oneWeekAgoStr};
            
            return db.delete(TABLE_DATA_POINTS, whereClause, whereArgs);
        } catch (Exception e) {
            Log.e(TAG, "删除旧数据失败: " + e.getMessage());
            return 0;
        } finally {
            db.close();
        }
    }
    
    /**
     * 获取数据点总数
     */
    public int getDataPointCount() {
        String countQuery = "SELECT COUNT(*) FROM " + TABLE_DATA_POINTS;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        int count = 0;
        if (cursor != null) {
            cursor.moveToFirst();
            count = cursor.getInt(0);
            cursor.close();
        }
        db.close();
        return count;
    }
    
    /**
     * 获取指定类型的数据点总数
     */
    public int getDataPointCountByType(int type) {
        String countQuery = "SELECT COUNT(*) FROM " + TABLE_DATA_POINTS + " WHERE " + COLUMN_TYPE + " = " + type;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        int count = 0;
        if (cursor != null) {
            cursor.moveToFirst();
            count = cursor.getInt(0);
            cursor.close();
        }
        db.close();
        return count;
    }
} 