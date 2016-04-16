package com.example.alexandr.tests3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.view.LineChartView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    //Экземпляры классов наших кнопок
    TextView tempV;
    TextView hemiV;
    TextView windV;
    Button dataUpdateBtn;
    ToggleButton pingBtn;
    ToggleButton windowBtn;
    LineChartView chart;

    List<Float> tempCollection = new LinkedList<>();
    List<Float> hemiCollection = new LinkedList<>();

    private static String hemiVal = "0.00";
    private static String tempVal = "0.00";
    private static char winStatus = '1';


    //Сокет, с помощью которого мы будем отправлять данные на Arduino
    BluetoothSocket clientSocket;

    //Эта функция запускается автоматически при запуске приложения
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chart = (LineChartView) findViewById(R.id.chart);

        //"Соединям" вид кнопки в окне приложения с реализацией

        dataUpdateBtn = (Button) findViewById(R.id.dataUpdate);
        pingBtn = (ToggleButton) findViewById(R.id.ping);
        windowBtn = (ToggleButton) findViewById(R.id.windowBtn);

        tempV = (TextView) findViewById(R.id.temp);
        hemiV = (TextView) findViewById(R.id.hemidy);
        windV = (TextView) findViewById(R.id.window);

        //Добавлем "слушатель нажатий" к кнопке

        dataUpdateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dataUpdate();
            }
        });

        pingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ping();
            }
        });
        windowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                window();
            }
        });


        btStart();
        new ArduinoReader();
        dataUpdate();
    }

    private void ping() {
        //Пытаемся послать данные
        OutputStream outStream = null;
        try {
            //Получаем выходной поток для передачи данных
            outStream = clientSocket.getOutputStream();
            //Пишем данные в выходной поток
            int value = (pingBtn.isChecked() ? 1 : 0) + 60;
            outStream.write(value);

        } catch (IOException e) {
            //Если есть ошибки, выводим их в лог
            Log.d("BLUETOOTH", e.getMessage());
        }
        viewUpdate();
    }

    private void window() {
        //Пытаемся послать данные
        OutputStream outStream = null;
        try {
            //Получаем выходной поток для передачи данных
            outStream = clientSocket.getOutputStream();
            //Пишем данные в выходной поток
            outStream.write(75);

        } catch (IOException e) {
            //Если есть ошибки, выводим их в лог
            Log.d("BLUETOOTH", e.getMessage());
        }
        windowBtn.setChecked(!windowBtn.isChecked());
        winStatus = winStatus == '0' ? '1' : '0';
        viewUpdate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void btStart() {
        //Включаем bluetooth. Если он уже включен, то ничего не произойдет
        String enableBT = BluetoothAdapter.ACTION_REQUEST_ENABLE;
        startActivityForResult(new Intent(enableBT), 0);

        //Мы хотим использовать тот bluetooth-адаптер, который задается по умолчанию
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();

        //Пытаемся проделать эти действия
        try {
            //Устройство с данным адресом - наш Bluetooth Bee
            //Адрес опредеяется следующим образом: установите соединение
            //между ПК и модулем (пин: 1234), а затем посмотрите в настройках
            //соединения адрес модуля. Скорее всего он будет аналогичным.
            BluetoothDevice device = bluetooth.getRemoteDevice("30:14:10:24:14:33");

            //Инициируем соединение с устройством
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);

            clientSocket = (BluetoothSocket) m.invoke(device, 1);
            clientSocket.connect();

            //В случае появления любых ошибок, выводим в лог сообщение
        } catch (IOException | SecurityException | IllegalArgumentException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            Log.d("BLUETOOTH", e.getMessage());
        }

        //Выводим сообщение об успешном подключении
        Toast.makeText(getApplicationContext(), "CONNECTED", Toast.LENGTH_LONG).show();
    }

    private void dataUpdate() {
        try {
            //Получаем выходной поток для передачи данных
            OutputStream outStream = clientSocket.getOutputStream();
            outStream.write(60);
        } catch (IOException e) {
            //Если есть ошибки, выводим их в лог
            Log.d("BLUETOOTH", e.getMessage());
        }
        viewUpdate();
    }

    private void viewUpdate() {
        hemiCollection.add(Float.valueOf(hemiVal.substring(0, 4)));
        tempCollection.add(Float.valueOf(tempVal.substring(0, 4)));
        tempV.setText(tempVal);
        hemiV.setText(hemiVal);
        if (winStatus == '0') {
            windV.setText("Окно закрыто");
            windowBtn.setChecked(false);
        } else {
            windV.setText("Окно открыто");
            windowBtn.setChecked(true);
        }

        updateChart();
    }

    private void updateChart() {
        if (tempCollection.size() > 10 && hemiCollection.size() > 10) {
            List<Float> temp = tempCollection;
            List<Float> hemi = hemiCollection;
            List<PointValue> valuesT = new ArrayList<>();
            for (int i = 0; i < hemi.size(); i++) {
                valuesT.add(new PointValue(i, hemi.get(i)));
            }
            List<PointValue> valuesH = new ArrayList<>();
            for (int i = 0; i < temp.size(); i++) {
                valuesH.add(new PointValue(i, temp.get(i)));
            }

            //In most cased you can call data model methods in builder-pattern-like manner.
            Line line1 = new Line(valuesH).setColor(Color.BLUE).setCubic(true);
            Line line2 = new Line(valuesT).setColor(Color.RED).setCubic(true);
            List<Line> lines = new ArrayList<>();
            lines.add(line1);
            lines.add(line2);

            LineChartData data = new LineChartData(lines);
            data.update(4);
            chart.setZoomType(ZoomType.HORIZONTAL_AND_VERTICAL);
            chart.setLineChartData(data);
        }

    }

    @Override
    public void onClick(View v) {

    }

    private class ArduinoReader implements Runnable {

        public ArduinoReader() {
            new Thread(this).start();
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()
            InputStream tmpIn = null;

            try {
                tmpIn = clientSocket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    assert tmpIn != null;
                    bytes = tmpIn.read(buffer);
                    String response = IOUtils.toString(buffer, "UTF-8");
                    System.out.println(response);

                    hemiVal = response.substring(0, 4) + " %";
                    tempVal = response.substring(5, 9) + " C'";
                    winStatus = response.charAt(10);

                } catch (IOException e) {
                    try {
                        tmpIn.close();
                    } catch (IOException ee) {
                        ee.printStackTrace();
                    }
                    break;
                }
            }
        }
    }
}
