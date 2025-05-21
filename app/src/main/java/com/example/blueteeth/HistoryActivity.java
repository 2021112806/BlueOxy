package com.example.blueteeth;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ListView historyListView;
    private Button clearButton;
    private Button backButton;
    private TextView emptyTextView;

    // 历史数据
    private ArrayList<DataPoint> historyData = new ArrayList<>();
    private HistoryDataAdapter adapter;

    // 数据库帮助类
    private DataDBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 初始化数据库帮助类
        dbHelper = DataDBHelper.getInstance(this);

        // 启用返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.history_data);
        }

        // 初始化控件
        historyListView = findViewById(R.id.list_history);
        clearButton = findViewById(R.id.btn_clear_history);
        backButton = findViewById(R.id.btn_back);
        emptyTextView = findViewById(R.id.txt_empty_history);

        // 加载历史数据
        loadHistoryData();

        // 设置适配器
        adapter = new HistoryDataAdapter();
        historyListView.setAdapter(adapter);

        // 更新空视图状态
        updateEmptyView();

        // 按钮点击事件
        clearButton.setOnClickListener(v -> showClearConfirmDialog());
        backButton.setOnClickListener(v -> finish());
    }

    // 加载历史数据
    private void loadHistoryData() {
        // 清除当前列表
        historyData.clear();

        try {
            // 从数据库中获取最近一周的数据
            ArrayList<DataPoint> dbData = dbHelper.getLastWeekData();

            // 添加到历史数据列表
            if (dbData != null && !dbData.isEmpty()) {
                historyData.addAll(dbData);

                // 记录日志
                Log.d("HistoryActivity", "已从数据库加载 " + dbData.size() + " 条历史数据");
            } else {
                Log.d("HistoryActivity", "数据库中没有历史数据");
            }
        } catch (Exception e) {
            Log.e("HistoryActivity", "加载历史数据失败: " + e.getMessage());
            Toast.makeText(this, "加载历史数据失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 更新空视图状态
    private void updateEmptyView() {
        if (historyData.isEmpty()) {
            emptyTextView.setVisibility(View.VISIBLE);
            historyListView.setVisibility(View.GONE);
        } else {
            emptyTextView.setVisibility(View.GONE);
            historyListView.setVisibility(View.VISIBLE);
        }
    }

    // 显示清除确认对话框
    private void showClearConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认清除");
        builder.setMessage("确定要清除所有历史记录吗？此操作无法撤销。");
        builder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                clearHistoryData();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    // 清除历史数据
    private void clearHistoryData() {
        try {
            // 清除数据库中的所有数据
            dbHelper.clearAllData();

            // 清除内存中的数据列表
            historyData.clear();

            // 通知适配器数据已更改
            adapter.notifyDataSetChanged();

            // 更新空视图状态
            updateEmptyView();

            // 显示成功消息
            Toast.makeText(this, "历史记录已清除", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("HistoryActivity", "清除历史数据失败: " + e.getMessage());
            Toast.makeText(this, "清除历史数据失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次恢复时刷新数据，以便显示最新数据
        loadHistoryData();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
            updateEmptyView();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 自定义数据适配器
    private class HistoryDataAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return historyData.size();
        }

        @Override
        public DataPoint getItem(int position) {
            return historyData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            ViewHolder holder;

            if (convertView == null) {
                view = getLayoutInflater().inflate(R.layout.item_history_data, parent, false);
                holder = new ViewHolder();
                holder.timestampTextView = view.findViewById(R.id.txt_timestamp);
                holder.typeTextView = view.findViewById(R.id.txt_data_type);
                holder.valueTextView = view.findViewById(R.id.txt_data_value);
                view.setTag(holder);
            } else {
                view = convertView;
                holder = (ViewHolder) view.getTag();
            }

            DataPoint dataPoint = getItem(position);

            // 设置时间戳
            holder.timestampTextView.setText(dataPoint.getTimestamp());

            // 设置数据类型
            String typeStr = "";
            switch (dataPoint.getType()) {
                case DataPoint.TYPE_RAW:
                    typeStr = "ADC值";
                    break;
                case DataPoint.TYPE_PERCENTAGE:
                    typeStr = "氧浓度";
                    break;
                case DataPoint.TYPE_VOLTAGE:
                    typeStr = "电压";
                    break;
            }
            holder.typeTextView.setText(typeStr);

            // 设置数据值
            holder.valueTextView.setText(dataPoint.getFormattedValue());

            // 设置氧浓度项目的特殊颜色
            if (dataPoint.getType() == DataPoint.TYPE_PERCENTAGE) {
                float value = dataPoint.getValue();
                if (value < 16.0f) {
                    holder.valueTextView.setTextColor(getResources().getColor(R.color.chart_red));
                } else {
                    holder.valueTextView.setTextColor(getResources().getColor(R.color.chart_blue));
                }
            } else {
                holder.valueTextView.setTextColor(getResources().getColor(R.color.primary_text));
            }

            return view;
        }

        // ViewHolder模式提高列表性能
        private class ViewHolder {
            TextView timestampTextView;
            TextView typeTextView;
            TextView valueTextView;
        }
    }
}