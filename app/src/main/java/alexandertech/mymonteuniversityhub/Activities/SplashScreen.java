package alexandertech.mymonteuniversityhub.Activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import alexandertech.mymonteuniversityhub.Classes.LiteDBHelper;
import alexandertech.mymonteuniversityhub.R;

public class SplashScreen extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        new CheckLoginStatus().execute();
    }

    private class CheckLoginStatus extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // before making db calls

        }

        @Override
        protected Boolean doInBackground(Void... arg0) {
            LiteDBHelper status = new LiteDBHelper(getApplicationContext());
            boolean isLoggedIn = status.getUserLoginStatus();
            try {
                Thread.sleep(1200);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            //returning false because perm login has NOT been set up therefor the isSessionvalid function in onPostExecute will not return true and show mainact.
            return isLoggedIn;

        }

        @Override
        protected void onPostExecute(final Boolean isSessionValid) {
            LiteDBHelper status = new LiteDBHelper(getApplicationContext());
            //here the session from the database is still valid... therefore we proceed with auto login
            if (isSessionValid) {
                Intent MainActivity = new Intent(SplashScreen.this, MainActivity.class);
                //Now using SharedPreferences instead of bundled extras
                putUserDataIntoSharedPreferences(status);

                startActivity(MainActivity);
            }
            //session is NOT valid, therefore we go to the login screen.
            else {
                Intent SplashScreenRedirect = new Intent(SplashScreen.this, LoginActivity.class);
                startActivity(SplashScreenRedirect);
            }
            // close this activity
            finish();
        }

        /**
         * Method to place all userdata into the SharedPreferences (must do this here in Splash to ensure SharedPrefs up-to-date
         * @param status
         */
        private void putUserDataIntoSharedPreferences(LiteDBHelper status) {
            SharedPreferences sharedPreferences = getSharedPreferences("MontePrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor prefEditor = sharedPreferences.edit();
            prefEditor.putString("First Name", status.getFName());
            prefEditor.putString("Last Name", status.getLName());
            prefEditor.putString("Email", status.getEmail());
            prefEditor.putString("ID", status.getID());
            prefEditor.putString("SessionKey", status.getSessionKey());
            prefEditor.apply();
        }


    }
}




