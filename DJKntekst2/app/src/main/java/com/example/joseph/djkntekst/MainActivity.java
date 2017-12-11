package com.example.joseph.djkntekst;

import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ListActivity {

    private TextView mTextMessage;
    private Context c = null;
    private ArrayAdapter adapter = null;
    String[] stuff = {};

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    stuff = new String[]{"stuff1", "stuff2"};
                    adapter = new ArrayAdapter(c,android.R.layout.simple_list_item_1, stuff);
                    setListAdapter(adapter);
                    return true;
                case R.id.navigation_dashboard:
                    stuff = new String[]{"stuff3", "stuff4"};
                    adapter = new ArrayAdapter(c,android.R.layout.simple_list_item_1, stuff);
                    setListAdapter(adapter);
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        c = this;
        setContentView(R.layout.activity_main);

        mTextMessage = (TextView) findViewById(R.id.message);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        stuff = new String[]{"stuff1", "stuff2"};
        adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, stuff);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Object o = this.getListAdapter().getItem(position);
        String pen = o.toString();
        Toast.makeText(this, "Playing: " + " " + pen, Toast.LENGTH_LONG).show();
    }

}
