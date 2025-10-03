package com.example.androidexample;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;



import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONObject;


import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameEditText;  // define username edittext variable
    private EditText passwordEditText;  // define password edittext variable

    private Button loginButton;

    private Button backButton;



    String url = "https://c5c54892-829a-4d77-bdcc-1d887f3cc81c.mock.pstmn.io/users";





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);            // link to Login activity XML

        /* initialize UI elements */
        usernameEditText = findViewById(R.id.login_username_edt); //Username text field
        passwordEditText = findViewById(R.id.login_password_edt); //Password text field
        loginButton = findViewById(R.id.login_login_btn);    //Login button
        backButton = findViewById(R.id.login_back_btn);




        /* click listener on login button pressed */
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {



                /* grab strings from user inputs */
                String username = usernameEditText.getText().toString();
                String password = passwordEditText.getText().toString();

                makeJsonObjReq(username, password);

                // Verify the user login attempt matches an account in our mock database
            }
        });

        backButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
    }

    private void makeJsonObjReq(final String username, final String password) {
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONArray usersArray = response.getJSONArray("users");
                            boolean found = false;

                            for (int i = 0; i < usersArray.length(); i++) {
                                JSONObject userObj = usersArray.getJSONObject(i);

                                String u = userObj.getString("username");
                                String p = userObj.getString("password");
                                boolean isAdmin = userObj.getBoolean("isAdmin");
                                boolean isModerator = userObj.getBoolean("isModerator");

                                // Check if user matches input
                                if (u.equals(username) && p.equals(password)) {
                                    found = true;

                                    if (isAdmin) {
                                        Intent intent = new Intent(LoginActivity.this, AdminActivity.class);
                                        intent.putExtra("USERNAME", u);
                                        Toast.makeText(getApplicationContext(), "Welcome Admin " + u + "!", Toast.LENGTH_LONG).show();
                                        startActivity(intent);
                                    } else if (isModerator) {
                                        Intent intent = new Intent(LoginActivity.this, ModeratorActivity.class);
                                        intent.putExtra("USERNAME", u);
                                        Toast.makeText(getApplicationContext(), "Welcome Moderator " + u + "!", Toast.LENGTH_LONG).show();
                                        startActivity(intent);
                                    } else {
                                        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                                        intent.putExtra("USERNAME", u);
                                        Toast.makeText(getApplicationContext(), "Welcome " + u + "!", Toast.LENGTH_LONG).show();
                                        startActivity(intent);
                                    }
                                    break; // stop loop once found
                                }
                            }

                            if (!found) {
                                Toast.makeText(getApplicationContext(), "User not found", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Log.e("Volley Parse Error", e.toString());

                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Volley Error", error.toString());

                    }
                }
        );

        VolleySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsonObjReq);
    }

}