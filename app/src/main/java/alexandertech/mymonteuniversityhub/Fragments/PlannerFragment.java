package alexandertech.mymonteuniversityhub.Fragments;


import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialog;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;

import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.CalendarMode;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

import alexandertech.mymonteuniversityhub.Adapters.TaskAdapter;
import alexandertech.mymonteuniversityhub.Classes.Assignment;
import alexandertech.mymonteuniversityhub.Classes.EventDecorator;
import alexandertech.mymonteuniversityhub.Classes.MonteApiHelper;
import alexandertech.mymonteuniversityhub.Classes.MyFirebaseInstanceIdService;
import alexandertech.mymonteuniversityhub.Classes.Task;
import alexandertech.mymonteuniversityhub.Interfaces.TaskItemClickListener;
import alexandertech.mymonteuniversityhub.R;

import static alexandertech.mymonteuniversityhub.Activities.MainActivity.sharedPrefs;

/**
 * Title: PlannerFragment
 * Authors: Joseph Molina, Anthony Symkowick
 * Date: 2/17/2017
 * Description: This file instantiates and connects the objects necessary for the PlannerFragment activity, including
 * a Calendar and a TaskList. The calendar will show students at-a-glance info about upcoming events via a custom Dialog.
 * The TaskList will be a dynamic set of Cards filled with user-defined TO-DO items.
 */

public class PlannerFragment extends Fragment {

    private TaskAdapter taskAdapter; //Custom adapter to preserve data integrity between server db & TaskList recyclerview
    private CardView mCardView; //GoogleNow-style Cards for the Task List
    private EventDecorator assignmentDot; //Allows indicator dots to be added to the calendar

    private MyFirebaseInstanceIdService firebaseInstance; //Firebase connection
    private SharedPreferences sharedPreferences;
    private MonteApiHelper monteApiHelper;
    private String userEmail = "";
    private String userFName = "";
    private String userLName = "";
    private String userID = "";

    private List<Task> tasks;
    private ArrayList<Assignment> uglyAssignments;


    View v;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        /**
         * First, we must:
         *      1. Connect to Firebase Instance for Notifications (Assignment Reminders)
         *      2. Connect to the MonteAPI (monteApiHelper)
         *      3. Inflate the views (UI elements)
         *      4. Prepare the ugly data for manipulation & display
         */

        // Step 1 \\
        //Database Connection Setup
        firebaseInstance = new MyFirebaseInstanceIdService();
        monteApiHelper = new MonteApiHelper(getContext());

        // Step 2 \\
        //Instantiate the list of Assignments retrieved from the server (still needs to be parsed)
        uglyAssignments = new ArrayList<>();

        // Step 3 \\
        // XML Layout is inflated for fragment_planner
        v = inflater.inflate(R.layout.fragment_planner, container, false); //Position of this line is important, be careful!
        View taskview = inflater.inflate(R.layout.tasklist, container, false);


        // Step 4 \\
        //Check server for upcoming Assignments
        try {
            uglyAssignments = requestAssignmentsFromServer();
            Log.d("Raw Assignment Data" , "All Data" + uglyAssignments.get(0).getName());

        } catch(Exception e) {
            Snackbar.make(getView(), "Whoops, I messed up grabbing Assignments :(", Snackbar.LENGTH_LONG).show();
        }

        //Assign the getAssignments function to a list of CalendarDay objects
        ArrayList<CalendarDay> dates = new ArrayList<>(); //CalendarDay is a special date wrapper from MaterialCalendarView library

        //Parse the Assignment Name, Course, and DueDate from the above list
        for(Assignment a : uglyAssignments)
        {
            String pattern = "MM dd YYYY"; //We like this date format, you can use any :)

            try{
                Log.d("Raw Assignment Data", "Due Date: " + a.getDuedate().toString());

                Date date = new Date ();
                date.setTime((long)Long.parseLong(a.getDuedate())*1000); //Multiplication by 1000 is REQUIRED to convert to UNIX time (millis)
                Log.d("dateish", date.toString());

                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                CalendarDay calendarDay = CalendarDay.from(cal);
                dates.add(calendarDay);

            } catch(Exception e) {
                Log.d("Raw Assignment Data", "Error parsing assignment data");
            }

        }

        /**
         * MaterialCalendarView - https://github.com/prolificinteractive/material-calendarview
         *      Assignments are represented in the calendar with the assignmentDot decorator.
         *      When a user taps a date, the corresponding assignments appear in an AlertDialog.
         */


        //Instantiate the Calendar
        final MaterialCalendarView calendarView = (MaterialCalendarView) v.findViewById(R.id.calendarView);

        //Instantiate the Dot Decorator
        assignmentDot = new EventDecorator(R.color.csumb_blue, dates);

        //Attach a listener to detect when a user taps a specific date
        calendarView.setOnDateChangedListener(new OnDateSelectedListener() {
            @Override
            public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {

                //An alert dialog will be shown -
                AlertDialog.Builder alert = new AlertDialog.Builder(getContext());

                // - to display the assignments in a listview!
                ListView modeList = new ListView(getContext());
                ArrayList<String> stringArrayList = new ArrayList<String>();
                alert.setTitle("Nothing due :)"); //Initialize w/ no assignments

                //Iterate through the Assignments and find the ones matching the selected date
                for(Assignment a : uglyAssignments)
                {
                    if(a.getDuedate() != "null")
                    {

                        Date d = new Date ();
                        d.setTime(Long.parseLong(a.getDuedate())*1000);

                        Calendar cal = Calendar.getInstance();
                        cal.setTime(d);
                        CalendarDay calendarDay = CalendarDay.from(cal);

                        //If date tapped = date with assignments due, show info
                        if(calendarDay.equals(date))
                        {
                            alert.setTitle("Hey, Don't Forget!");
                            stringArrayList.add((a.getCourse() + ": " + a.getName()));
                        }

                    }

                }

                //Setup simple listview adapter
                ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1, android.R.id.text1, stringArrayList);
                modeList.setAdapter(modeAdapter);
                alert.setView(modeList);
                alert.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
                alert.show();

            }
        });

        calendarView.state().edit()
                .setCalendarDisplayMode(CalendarMode.WEEKS)
                .commit();
        calendarView.addDecorator(assignmentDot);

        //Instantiate FAB
        FloatingActionButton addTaskFab = (FloatingActionButton) v.findViewById(R.id.fab);
        addTaskFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    launchAddTaskDialog();
                } catch(Exception e) {
                    //TODO: handle exception
                }
            }
        });


        /*
         * Before we return the inflated view, we will instantiate a RecyclerView object and reference the xml element.
         * The RecyclerView will serve as the manager for dynamic Tasks in the TaskList.
         * For info about the container and/or how fragments work compared to activities,
         * see https://developer.android.com/guide/components/fragments.html#Creating
         */

        // Connect RecyclerView (cardList) and set its layout manager
        RecyclerView recList = (RecyclerView) v.findViewById(R.id.cardList);
        recList.setHasFixedSize(true); //boost memory-management efficiency by ensuring the list is not redrawn if height doesn't change when an element is added

        //A LayoutManager ensures the RecyclerView behaves like a listview
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        recList.setLayoutManager(llm);

        /**
         * TaskList Sync - Custom Adapter written to keep tasks in sync with server data
         */

        try {
            //Initialize TaskList with tasks from server
            tasks = requestTasksFromServer();
        } catch (Exception e) {
            //Make a brand new empty list if not found on server
            tasks = new ArrayList<>();
            Log.d("TasksFromServer", "request failed...");
        }

        taskAdapter = new TaskAdapter(getContext(), tasks, new TaskItemClickListener() {
            @Override
            public void onItemClick(View v, int position) {
                try {

                    //Show the task that a user tapped
                    launchViewTaskDialog(tasks.get(position)); //TODO: fix the tasklist duedate display bug - indexOf? (position is wrong)
                    Log.d("VerifyTaskTime", tasks.get(position).getDueDate().toString());

                } catch(IOException e) {
                    Snackbar.make(v, "Error finding info for task \"" + tasks.get(position).getName() + "\"", Snackbar.LENGTH_SHORT).show();
                }
            }
        }); //Connect ArrayList to the Adapter
        recList.setAdapter(taskAdapter); //Connect RecyclerView to the Adapter
        //So, it goes RecyclerView -> Adapter <- TaskArrayList :)


        //This helps the BottomSheetDialog handle keyboard input without hiding the Date & Time Buttons
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        return v;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    public void launchAddTaskDialog() throws ExecutionException,InterruptedException {
        final BottomSheetDialog addTaskDialog = new BottomSheetDialog(getActivity());
        final View addTaskLayout = getActivity().getLayoutInflater().inflate(R.layout.bottomsheetdialog_addtask, null);
        addTaskDialog.setContentView(addTaskLayout);
        addTaskDialog.show();

        final Calendar todayDate = Calendar.getInstance();
        final Calendar selectedDate = Calendar.getInstance(); //Initialize to today's date

        final Button btnSave = (Button) addTaskLayout.findViewById(R.id.btnSaveTask);
        ImageButton btnDueDate = (ImageButton) addTaskLayout.findViewById(R.id.btnDueDate);
        ImageButton btnDueTime = (ImageButton) addTaskLayout.findViewById(R.id.btnDueTime);
        final EditText taskEditText = (EditText) addTaskLayout.findViewById(R.id.addTaskContent);

        //Start Handle Disable Empty Task Uploads
        btnSave.setEnabled(false);
        taskEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if(s.toString().trim().length() > 0)
                {
                    btnSave.setEnabled(true);
                }
            }
        });
        //End Handle Disable Empty Task Uploads


        //Tapping the Due Date launches a DatePickerDialog
        btnDueDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        selectedDate.set(year, month, dayOfMonth);
                        TextView dueDateText = (TextView) addTaskLayout.findViewById(R.id.dueDateText);
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, MMM d");

                        dueDateText.setText(simpleDateFormat.format(selectedDate.getTime()));
                    }
                }, todayDate.get(Calendar.YEAR), todayDate.get(Calendar.MONTH), todayDate.get(Calendar.DAY_OF_MONTH));

                datePickerDialog.getDatePicker().setMinDate(todayDate.getTimeInMillis()); //Tasks can't be set in the past
                datePickerDialog.show();

            }
        });

        //Tapping the clock icon launches a TimePickerDialog
        btnDueTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimePickerDialog timePickerDialog = new TimePickerDialog(getContext(), new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        selectedDate.set(Calendar.MINUTE, minute);
                        TextView dueTimeText = (TextView) addTaskLayout.findViewById(R.id.dueTimeText);
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("h:mm a");

                        dueTimeText.setText(simpleDateFormat.format(selectedDate.getTime()));
                    }
                }, todayDate.get(Calendar.HOUR_OF_DAY), todayDate.get(Calendar.MINUTE), false);
                timePickerDialog.show();
            }
        });


        //Tapping the Save button stores all user input on the server
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                monteApiHelper = new MonteApiHelper(getContext());
                sharedPreferences = getActivity().getSharedPreferences("MontePrefs", Context.MODE_PRIVATE);
                userFName = sharedPrefs.getString("First Name", "Monte"); //SharedPreferences retrieval takes Key and DefaultValue as parameters
                userLName = sharedPrefs.getString("Last Name", "Otter");
                userEmail = sharedPrefs.getString("Email", "monte@ottermail.com");
                userID = sharedPrefs.getString("ID", "12345");

                final String taskTitle = taskEditText.getText().toString();
                final String selectedDateString = Long.toString((selectedDate.getTime().getTime() / 1000)); //gotta convert from cal to date to Unixtime
                SimpleDateFormat prettyDueDate = new SimpleDateFormat("MMM d, h:mm a");

                RunnableFuture f = new FutureTask(new Callable() {
                    public Integer call() {
                        try {
                            //Tasks created on the device do not get an ID until they are inserted into the server-side database
                            int taskID = monteApiHelper.insertTask(taskTitle, userID, selectedDateString, firebaseInstance.getFirebaseAndroidID());
                            Log.d("Timestamp Accuracy", selectedDateString);
                            return taskID;
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        //If above failed
                        return 666;
                    }
                });

                new Thread(f).start();

                int taskID = 0;
                try {
                     taskID = (Integer) f.get();
                } catch(Exception e) {
                    Log.d("TaskIntConversionError", "oops int");
                }

                //Now that the task has been given an ID by the server, we can add it to our adapter
                Task t = new Task(taskTitle, selectedDate, taskID);
                tasks.add(t);

                //Upon notifying the adapter of a data change, the RecyclerView will be populated with our new Task :)
                taskAdapter.notifyDataSetChanged();
                addTaskDialog.closeOptionsMenu();
                addTaskDialog.dismiss();
                Snackbar.make(getView(), "Saved \"" + taskTitle + "\" for " + prettyDueDate.format(selectedDate.getTime()), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    public void launchViewTaskDialog(final Task taskSelected) throws IOException {
        final Task t = taskSelected;
        final BottomSheetDialog viewTaskDialog = new BottomSheetDialog(getActivity());
        final View viewTaskLayout = getActivity().getLayoutInflater().inflate(R.layout.bottomsheetdialog_deletetask, null); //re-using this layout, tweaking into a View-Only version

        /**
         * Populate the bottomsheetdialog with specific task data.
         * This includes Title TextView, Date TextView, Time TextView, and a Delete button.
         */

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d"); //Formatter for Date (May 4)
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a");//Formatter for Time (6:00pm)

        Log.d("DueDateTextView", taskSelected.getDueDate().toString());
        TextView title = (TextView) viewTaskLayout.findViewById(R.id.txtTaskName); //Title of task
        TextView date = (TextView) viewTaskLayout.findViewById(R.id.dueDateText); //Date of task
        TextView time = (TextView) viewTaskLayout.findViewById(R.id.dueTimeText); //Time of task

        title.setText(taskSelected.getName());
        date.setText(dateFormat.format(taskSelected.getDueDate().getTime()));
        time.setText(timeFormat.format(taskSelected.getDueDate().getTime()));


        Button delete = (Button) viewTaskLayout.findViewById(R.id.btnDeleteTask);

        viewTaskDialog.setContentView(viewTaskLayout);
        viewTaskDialog.show();

        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    deleteTask(t);
                    tasks.remove(t);
                    viewTaskDialog.dismiss();
                    Snackbar.make(getView(), "Deleted task!", Snackbar.LENGTH_SHORT).show();
                } catch(IOException e) {
                    viewTaskDialog.dismiss();
                    Snackbar.make(getView(), "Oops, internal server error!", Snackbar.LENGTH_SHORT).show();
                    Log.d("DeleteError", e.toString());
                }
            }
        });

    }

    public ArrayList<Task> requestTasksFromServer() throws IOException,ExecutionException,InterruptedException {
        final MonteApiHelper monteApiHelper = new MonteApiHelper(getContext());
        sharedPreferences = getActivity().getSharedPreferences("MontePrefs", Context.MODE_PRIVATE);
        userID = sharedPrefs.getString("ID", "12345");
        ArrayList<Task> result;

        //RunnableFuture allows arraylist to be populated in a separate thread, and then returned to the original thread
        RunnableFuture f = new FutureTask(new Callable() {
            public ArrayList<Task> call() throws IOException{
                ArrayList<Task> t = monteApiHelper.getTasksFromServer(userID);
                return t;
            }
        });

        new Thread(f).start();
        result = (ArrayList) f.get();
        return result;
    }

    public void deleteTask(final Task t) throws IOException {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final MonteApiHelper monteApiHelper = new MonteApiHelper(getContext());
                    sharedPreferences = getActivity().getSharedPreferences("MontePrefs", Context.MODE_PRIVATE);
                    userID = sharedPrefs.getString("ID", "12345");
                    Log.d("Object task id", Integer.toString(t.getId()));
                    monteApiHelper.deleteTask(userID, t.getId());
                    return;

                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
        taskAdapter.notifyDataSetChanged();

    }

    public ArrayList<Assignment> requestAssignmentsFromServer() throws Exception {
        final MonteApiHelper monteApiHelper = new MonteApiHelper(getContext());
        sharedPreferences = getActivity().getSharedPreferences("MontePrefs", Context.MODE_PRIVATE);
        userID = sharedPrefs.getString("ID", "12345");
        ArrayList<Assignment> assignmentsUgly;

        //RunnableFuture allows arraylist to be populated in a separate thread, and then returned
        RunnableFuture f = new FutureTask(new Callable() {
            public ArrayList<Assignment> call() throws IOException{
                ArrayList<Assignment> t = monteApiHelper.getAssignmentsFromServer(userID);
                return t;
            }
        });

        new Thread(f).start();
        assignmentsUgly = (ArrayList) f.get();

        return assignmentsUgly;

    }

}