package com.example.blueteeth;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.os.Handler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChartActivity extends AppCompatActivity {

    private static final int CHART_TYPE_LINE = 0;
    private static final int CHART_TYPE_BAR = 1;
    private static final int CHART_TYPE_PIE = 2;

    private LineChart lineChart;
    private BarChart barChart;
    private PieChart pieChart;
    private Button refreshButton;
    private Button backButton;
    private Spinner chartTypeSpinner;
    private TextView analysisTextView;

    private ArrayList<DataPoint> dataPoints;
    private int currentChartType = CHART_TYPE_LINE;

    // 添加广播接收器，接收数据更新
    private final BroadcastReceiver dataUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.blueteeth.DATA_UPDATED".equals(intent.getAction())) {
                Log.d("ChartActivity", "收到数据更新");
                ArrayList<DataPoint> updatedData = intent.getParcelableArrayListExtra("data_points");
                if (updatedData != null && !updatedData.isEmpty()) {
                    // 更新数据
                    dataPoints.clear();
                    dataPoints.addAll(updatedData);

                    // 重绘图表
                    updateChartData();

                    // 显示更新完成提示
                    Toast.makeText(ChartActivity.this, "数据已更新", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChartActivity.this, "暂无新数据", Toast.LENGTH_SHORT).show();
                }

                // 恢复刷新按钮状态
                refreshButton.setEnabled(true);
                refreshButton.setText(R.string.refresh);
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        // 启用返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.data_chart);
        }

        // 初始化控件
        lineChart = findViewById(R.id.line_chart);
        barChart = findViewById(R.id.bar_chart);
        pieChart = findViewById(R.id.pie_chart);
        refreshButton = findViewById(R.id.btn_refresh);
        backButton = findViewById(R.id.btn_back);
        chartTypeSpinner = findViewById(R.id.spinner_chart_type);
        analysisTextView = findViewById(R.id.txt_analysis);

        // 设置图表选择器
        setupChartTypeSpinner();

        // 获取数据点列表
        if (getIntent().hasExtra("data_points")) {
            dataPoints = getIntent().getParcelableArrayListExtra("data_points");
        } else {
            dataPoints = new ArrayList<>();
        }

        // 设置图表
        setupLineChart();
        setupBarChart();
        setupPieChart();

        // 显示默认图表
        showChart(currentChartType);

        // 添加数据到图表
        updateChartData();

        // 按钮点击事件
        refreshButton.setOnClickListener(v -> {
            // 添加按钮视觉反馈
            refreshButton.setEnabled(false);
            refreshButton.setText(R.string.refreshing);

            // 显示刷新中的提示
            Toast.makeText(this, "正在从主界面获取最新数据...", Toast.LENGTH_SHORT).show();

            // 用动画效果清除图表，等待新数据
            clearChartsWithAnimation();

            // 发送广播请求最新数据
            Intent intent = new Intent("com.example.blueteeth.REQUEST_DATA");
            sendBroadcast(intent);

            // 注意：不在这里更新图表，而是在收到数据更新广播时更新
            // 如果2秒内没收到响应，恢复按钮状态
            new Handler().postDelayed(() -> {
                if (!refreshButton.isEnabled()) {
                    refreshButton.setEnabled(true);
                    refreshButton.setText(R.string.refresh);
                    Toast.makeText(ChartActivity.this, "获取数据超时，请重试", Toast.LENGTH_SHORT).show();
                }
            }, 2000);
        });

        backButton.setOnClickListener(v -> finish());

        // 注册广播接收器
        IntentFilter filter = new IntentFilter("com.example.blueteeth.DATA_UPDATED");
        registerReceiver(dataUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 注销广播接收器
        unregisterReceiver(dataUpdateReceiver);
    }

    private void setupChartTypeSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.chart_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chartTypeSpinner.setAdapter(adapter);

        chartTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentChartType = position;
                showChart(currentChartType);
                updateChartData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做任何处理
            }
        });
    }

    private void showChart(int chartType) {
        // 隐藏所有图表
        lineChart.setVisibility(View.GONE);
        barChart.setVisibility(View.GONE);
        pieChart.setVisibility(View.GONE);

        // 显示选中的图表
        switch (chartType) {
            case CHART_TYPE_LINE:
                lineChart.setVisibility(View.VISIBLE);
                break;
            case CHART_TYPE_BAR:
                barChart.setVisibility(View.VISIBLE);
                break;
            case CHART_TYPE_PIE:
                pieChart.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void setupLineChart() {
        // 配置图表
        lineChart.setDrawGridBackground(false);
        lineChart.setDrawBorders(true);
        lineChart.setBorderColor(Color.LTGRAY);
        lineChart.setBorderWidth(1f);

        // 图表描述
        Description description = new Description();
        description.setText("氧浓度数据曲线图");
        description.setTextColor(Color.GRAY);
        lineChart.setDescription(description);

        // X轴配置
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(true);
        xAxis.setLabelCount(5, true); // 减少标签数量，避免拥挤

        // Y轴配置
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);

        // 图例配置
        Legend legend = lineChart.getLegend();
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setTextSize(12f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);

        // 交互设置
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDoubleTapToZoomEnabled(true);

        // 动画
        lineChart.animateX(1500);
    }

    private void setupBarChart() {
        // 配置柱状图
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setPinchZoom(false);
        barChart.setDrawGridBackground(false);

        // 图表描述
        Description description = new Description();
        description.setText("氧浓度分布");
        description.setTextColor(Color.GRAY);
        barChart.setDescription(description);

        // X轴配置
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(8, false);

        // Y轴配置
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);

        YAxis rightAxis = barChart.getAxisRight();
        rightAxis.setEnabled(false);

        // 图例配置
        Legend legend = barChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setForm(Legend.LegendForm.SQUARE);
        legend.setFormSize(9f);
        legend.setTextSize(11f);
        legend.setXEntrySpace(4f);

        // 动画
        barChart.animateY(1500);
    }

    private void setupPieChart() {
        // 配置饼图
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(true);
        pieChart.getDescription().setText("氧浓度范围分布");
        pieChart.setExtraOffsets(5, 10, 5, 5);

        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setCenterText("氧浓度分布");
        pieChart.setCenterTextSize(16f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleColor(Color.WHITE);
        pieChart.setTransparentCircleAlpha(110);
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setDrawCenterText(true);
        pieChart.setRotationAngle(0);
        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);

        // 图例配置
        Legend legend = pieChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setXEntrySpace(7f);
        legend.setYEntrySpace(0f);
        legend.setYOffset(10f);

        // 动画
        pieChart.animateY(1500);
    }

    private void updateChartData() {
        if (dataPoints == null || dataPoints.isEmpty()) {
            lineChart.setNoDataText("暂无数据");
            barChart.setNoDataText("暂无数据");
            pieChart.setNoDataText("暂无数据");
            return;
        }

        // 只筛选氧浓度数据
        List<DataPoint> percentageDataPoints = new ArrayList<>();
        for (DataPoint point : dataPoints) {
            if (point.getType() == DataPoint.TYPE_PERCENTAGE) {
                percentageDataPoints.add(point);
            }
        }

        // 根据当前图表类型更新数据
        switch (currentChartType) {
            case CHART_TYPE_LINE:
                updateLineChart(percentageDataPoints);
                break;
            case CHART_TYPE_BAR:
                updateBarChart(percentageDataPoints);
                break;
            case CHART_TYPE_PIE:
                updatePieChart(percentageDataPoints);
                break;
        }

        // 更新数据分析文本
        updateAnalysisText(percentageDataPoints);
    }

    private void updateLineChart(List<DataPoint> percentageDataPoints) {
        // 清除旧数据
        lineChart.clear();

        // 创建多个数据集
        List<LineDataSet> dataSets = new ArrayList<>();

        // 只添加氧浓度数据集
        if (!percentageDataPoints.isEmpty()) {
            LineDataSet percentageDataSet = createLineDataSet(percentageDataPoints, "氧浓度(%)",
                    getResources().getColor(R.color.chart_red), 0);
            dataSets.add(percentageDataSet);
        }

        // 如果有数据集，设置到图表
        if (!dataSets.isEmpty()) {
            LineData lineData = new LineData(dataSets.toArray(new LineDataSet[0]));
            lineChart.setData(lineData);

            // 设置Y轴范围为自动调整
            lineChart.getAxisLeft().setAxisMinimum(0f);
            lineChart.getAxisLeft().resetAxisMaximum();

            // 新图表刷
            lineChart.invalidate();
        }
    }

    // 创建折线数据集的辅助方法
    private LineDataSet createLineDataSet(List<DataPoint> dataPoints, String label, int color, int index) {
        List<Entry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();

        // 填充数据
        for (int i = 0; i < dataPoints.size(); i++) {
            DataPoint point = dataPoints.get(i);
            xLabels.add(point.getTimestamp());
            entries.add(new Entry(i, point.getValue()));
        }

        // 设置X轴标签
        if (!xLabels.isEmpty()) {
            XAxis xAxis = lineChart.getXAxis();
            // 移除第一个标签以避免与Y轴冲突
            if (xLabels.size() > 1) {
                xLabels.set(0, "");
            }

            xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));
            // 根据数据点数量动态调整标签数量
            int labelCount = Math.min(5, xLabels.size());
            xAxis.setLabelCount(labelCount, true);
        }

        // 创建数据集
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawFilled(true);
        dataSet.setFillAlpha(50);
        dataSet.setFillColor(color);
        dataSet.setHighlightEnabled(true);
        // 不显示每个点的数值
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // 使用曲线而不是直线
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);

        return dataSet;
    }

    private void updateBarChart(List<DataPoint> percentageDataPoints) {
        if (percentageDataPoints.isEmpty()) {
            barChart.setNoDataText("暂无氧浓度数据");
            return;
        }

        // 清除旧数据
        barChart.clear();

        // 对氧浓度数据进行范围分组
        Map<String, Integer> rangeMap = new HashMap<>();
        float minValue = Float.MAX_VALUE;
        float maxValue = Float.MIN_VALUE;

        // 找出最小值和最大值
        for (DataPoint point : percentageDataPoints) {
            float value = point.getValue();
            minValue = Math.min(minValue, value);
            maxValue = Math.max(maxValue, value);
        }

        // 创建范围区间
        float range = (maxValue - minValue) / 5;
        for (int i = 0; i < 5; i++) {
            float lowerBound = minValue + i * range;
            float upperBound = lowerBound + range;
            String rangeLabel = String.format("%.1f-%.1f%%", lowerBound, upperBound);
            rangeMap.put(rangeLabel, 0);
        }

        // 统计每个范围的数据点数量
        for (DataPoint point : percentageDataPoints) {
            float value = point.getValue();
            for (int i = 0; i < 5; i++) {
                float lowerBound = minValue + i * range;
                float upperBound = lowerBound + range;
                if (value >= lowerBound && (value < upperBound || (i == 4 && value == upperBound))) {
                    String rangeLabel = String.format("%.1f-%.1f%%", lowerBound, upperBound);
                    rangeMap.put(rangeLabel, rangeMap.get(rangeLabel) + 1);
                    break;
                }
            }
        }

        // 创建柱状图数据
        List<BarEntry> entries = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();
        int index = 0;

        for (Map.Entry<String, Integer> entry : rangeMap.entrySet()) {
            entries.add(new BarEntry(index, entry.getValue()));
            xLabels.add(entry.getKey());
            index++;
        }

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));
        xAxis.setLabelRotationAngle(45); // 旋转标签避免重叠

        BarDataSet dataSet = new BarDataSet(entries, "氧浓度分布");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.BLACK);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f);

        barChart.setData(barData);
        barChart.invalidate();
    }

    private void updatePieChart(List<DataPoint> percentageDataPoints) {
        if (percentageDataPoints.isEmpty()) {
            pieChart.setNoDataText("暂无氧浓度数据");
            return;
        }

        // 清除旧数据
        pieChart.clear();

        // 对氧浓度数据进行范围分组
        // 分析氧浓度分布 (<16%, 16-20%, 20-23.5%, >23.5%)
        int belowThreshold = 0;    // <16%
        int normalLow = 0;         // 16-20%
        int normalHigh = 0;        // 20-23.5%
        int aboveNormal = 0;       // >23.5%

        for (DataPoint point : percentageDataPoints) {
            float value = point.getValue();
            if (value < 16.0f) {
                belowThreshold++;
            } else if (value < 20.0f) {
                normalLow++;
            } else if (value < 23.5f) {
                normalHigh++;
            } else {
                aboveNormal++;
            }
        }

        // 创建饼图数据
        List<PieEntry> entries = new ArrayList<>();
        if (belowThreshold > 0) entries.add(new PieEntry(belowThreshold, "低氧 (<16%)"));
        if (normalLow > 0) entries.add(new PieEntry(normalLow, "正常低段 (16-20%)"));
        if (normalHigh > 0) entries.add(new PieEntry(normalHigh, "正常高段 (20-23.5%)"));
        if (aboveNormal > 0) entries.add(new PieEntry(aboveNormal, "高氧 (>23.5%)"));

        PieDataSet dataSet = new PieDataSet(entries, "氧浓度分布");

        // 设置饼图颜色
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.rgb(204, 0, 0));         // 红色 - 低氧
        colors.add(Color.rgb(255, 204, 0));       // 橙黄色 - 正常低段
        colors.add(Color.rgb(0, 153, 51));        // 绿色 - 正常高段
        colors.add(Color.rgb(0, 102, 204));       // 蓝色 - 高氧
        dataSet.setColors(colors);

        dataSet.setDrawIcons(false);

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new PercentFormatter(pieChart));
        pieData.setValueTextSize(12f);
        pieData.setValueTextColor(Color.WHITE);

        pieChart.setData(pieData);
        pieChart.invalidate();
    }

    private void updateAnalysisText(List<DataPoint> percentageDataPoints) {
        if (percentageDataPoints.isEmpty()) {
            analysisTextView.setText("暂无氧浓度数据可供分析");
            return;
        }

        // 计算基本统计数据
        float sum = 0;
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        DataPoint minPoint = null;
        DataPoint maxPoint = null;
        int belowThreshold = 0;

        for (DataPoint point : percentageDataPoints) {
            float value = point.getValue();
            sum += value;

            if (value < min) {
                min = value;
                minPoint = point;
            }

            if (value > max) {
                max = value;
                maxPoint = point;
            }

            if (value < 16.0f) {
                belowThreshold++;
            }
        }

        float avg = sum / percentageDataPoints.size();
        float belowPercent = (float) belowThreshold / percentageDataPoints.size() * 100;

        // 构建分析文本
        StringBuilder analysis = new StringBuilder();
        analysis.append("• 平均氧浓度: ").append(String.format("%.2f%%", avg)).append("\n");
        analysis.append("• 最低氧浓度: ").append(String.format("%.2f%% (时间: %s)", min, minPoint.getTimestamp())).append("\n");
        analysis.append("• 最高氧浓度: ").append(String.format("%.2f%% (时间: %s)", max, maxPoint.getTimestamp())).append("\n");
        analysis.append("• 低氧(<16%)比例: ").append(String.format("%.1f%%", belowPercent)).append("\n");
        analysis.append("• 数据点总数: ").append(percentageDataPoints.size());

        // 添加简单的趋势分析
        if (percentageDataPoints.size() >= 5) {
            float firstAvg = 0;
            float lastAvg = 0;

            // 计算前5个点和后5个点的平均值
            int count = Math.min(5, percentageDataPoints.size() / 3);
            for (int i = 0; i < count; i++) {
                firstAvg += percentageDataPoints.get(i).getValue();
            }
            firstAvg /= count;

            for (int i = percentageDataPoints.size() - count; i < percentageDataPoints.size(); i++) {
                lastAvg += percentageDataPoints.get(i).getValue();
            }
            lastAvg /= count;

            float change = lastAvg - firstAvg;
            analysis.append("\n\n• 趋势分析: ");
            if (Math.abs(change) < 0.5) {
                analysis.append("氧浓度保持稳定");
            } else if (change > 0) {
                analysis.append(String.format("氧浓度呈上升趋势，增加了%.2f%%", change));
            } else {
                analysis.append(String.format("氧浓度呈下降趋势，减少了%.2f%%", Math.abs(change)));
            }
        }

        analysisTextView.setText(analysis.toString());
    }

    // 添加清除图表并应用动画效果的方法
    private void clearChartsWithAnimation() {
        // 清除折线图并应用动画
        lineChart.clear();
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
        lineChart.animateY(1000);

        // 清除柱状图并应用动画
        barChart.clear();
        barChart.notifyDataSetChanged();
        barChart.invalidate();
        barChart.animateY(1000);

        // 清除饼图并应用动画
        pieChart.clear();
        pieChart.notifyDataSetChanged();
        pieChart.invalidate();
        pieChart.animateY(1000);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}