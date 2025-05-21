package com.example.blueteeth;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 数据点类，存储蓝牙接收到的数据和时间戳
 */
public class DataPoint implements Parcelable {
    public static final int TYPE_RAW = 0;
    public static final int TYPE_PERCENTAGE = 1;
    public static final int TYPE_VOLTAGE = 2;

    private String timestamp; // 数据时间戳
    private float value; // 数据值
    private int type; // 数据类型
    private String unit; // 数据单位

    public DataPoint(String timestamp, float value, int type) {
        this.timestamp = timestamp;
        this.value = value;
        this.type = type;

        // 根据类型设置单位
        switch (type) {
            case TYPE_PERCENTAGE:
                this.unit = "%";
                break;
            case TYPE_VOLTAGE:
                this.unit = "V";
                break;
            default:
                this.unit = "";
                break;
        }
    }

    protected DataPoint(Parcel in) {
        timestamp = in.readString();
        value = in.readFloat();
        type = in.readInt();
        unit = in.readString();
    }

    public static final Creator<DataPoint> CREATOR = new Creator<DataPoint>() {
        @Override
        public DataPoint createFromParcel(Parcel in) {
            return new DataPoint(in);
        }

        @Override
        public DataPoint[] newArray(int size) {
            return new DataPoint[size];
        }
    };

    public String getTimestamp() {
        return timestamp;
    }

    public float getValue() {
        return value;
    }

    public int getType() {
        return type;
    }

    public String getUnit() {
        return unit;
    }

    public String getFormattedValue() {
        if (type == TYPE_RAW) {
            return String.format("%.0f", value);
        } else {
            return String.format("%.2f%s", value, unit);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(timestamp);
        dest.writeFloat(value);
        dest.writeInt(type);
        dest.writeString(unit);
    }
}