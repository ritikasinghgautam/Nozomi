package com.example.missingpersons;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {

    //ui
    private EditText usernameEditText, passwordEditText;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initUI();
        initClicks();
    }

    private void initClicks() {
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username = usernameEditText.getText().toString();
                String password = passwordEditText.getText().toString();

                if(username.equals("") || password.equals("")){
                    Toast.makeText(LoginActivity.this, "Please fill all fields!", Toast.LENGTH_SHORT).show();
                } else{
                    if(username.equals("admin") && password.equals("admin123")){
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.putExtra("activity", "login");
                        startActivity(intent);
                    } else{
                        Toast.makeText(LoginActivity.this, "Please enter the correct credentials!", Toast.LENGTH_SHORT).show();
                    }
                }

            }
        });
    }

    private void initUI() {
        usernameEditText = findViewById(R.id.edittext_username);
        passwordEditText = findViewById(R.id.edittext_password);
        loginButton = findViewById(R.id.button_login);
    }
}