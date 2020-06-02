package com.anantdevelopers.swipesinalpha.PreviousOrdersFragment;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.anantdevelopers.swipesinalpha.CartFragment.CheckoutFlow.CheckoutUser;
import com.anantdevelopers.swipesinalpha.HomeFragment.FruitItem.FruitItem;
import com.anantdevelopers.swipesinalpha.MainActivity;
import com.anantdevelopers.swipesinalpha.PreviousOrdersFragment.PreviousOrderLocalDatabase.PreviousOrderEntity;
import com.anantdevelopers.swipesinalpha.PreviousOrdersFragment.PreviousOrderLocalDatabase.PreviousOrderViewModel;
import com.anantdevelopers.swipesinalpha.PreviousOrdersFragment.PreviousOrderLocalDatabase.RecyclerViewAdapterForPreviousOrders;
import com.anantdevelopers.swipesinalpha.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.view.View.GONE;


public class PreviousOrdersFragment extends Fragment implements DeletePreviousOrdersDialog.DeletePreviousOrdersDialogListener, CancelCurrentOrderDialog.cancelCurrentOrderDialogListener {

     private static final String SORTING_ORDER_1 = "newest_first";
     private static final String SORTING_ORDER_2 = "oldest_first";
     private static final String SORTING_ORDER_TYPE_STORAGE_KEY = "sorting_type";

     private RecyclerView currentOrdersRecyclerView;

     private DatabaseReference databaseReference;

     private ArrayList<CheckoutUser> currentOrdersList;
     private ArrayList<CheckoutUser> previousOrdersList; //for local database

     private ProgressBar currentOrderProgressBar;
     private TextView noCurrentOrdersTextView;
     private LinearLayout parentLayout;
     private ProgressBar previousOrderProgressBar;
     private TextView noPreviousOrdersTextView;

     private RecyclerViewAdapterForCurrentOrders adapterForCurrentOrders;
     private RecyclerViewAdapterForPreviousOrders adapter;

     private PreviousOrderViewModel previousOrderViewModel;

     private ValueEventListener valueEventListener, valueEventListener2;

     private String authPhoneNumber;
     private String token;

     private SharedPreferences sharedPreferences;
     private String sortingOrderType;

     private Observer<List<PreviousOrderEntity>> observer;

     public PreviousOrdersFragment() {
          // Required empty public constructor
     }

     private interface DatabaseCallbackInterface { //interface is used to handle asynchronous calls of firebase
          void fromOnChildManipulated(ArrayList<CheckoutUser> currentOrdersList);
     }

     private interface LocalDatabaseCallbackInterface {
          void fromOnChildManipulated(ArrayList<CheckoutUser> previousOrdersList);
     }

     @Override
     public void onCreate(@Nullable Bundle savedInstanceState) {
          super.onCreate(savedInstanceState);

          setHasOptionsMenu(true);

          sharedPreferences = getActivity().getPreferences(Context.MODE_PRIVATE);

          FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
          databaseReference = firebaseDatabase.getReference();
          FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

          authPhoneNumber = firebaseAuth.getCurrentUser().getPhoneNumber();

          currentOrdersList = new ArrayList<>();
          previousOrdersList = new ArrayList<>();

          previousOrderViewModel = new ViewModelProvider(this).get(PreviousOrderViewModel.class);

          getToken();
     }

     private void getToken() {
          FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
               @Override
               public void onSuccess(InstanceIdResult instanceIdResult) {
                    token = instanceIdResult.getToken();
               }
          });
     }

     @Override
     public View onCreateView(LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState) {
          // Inflate the layout for this fragment
          View v = inflater.inflate(R.layout.fragment_previous_orders, container, false);

          parentLayout = v.findViewById(R.id.parent_layout);

          currentOrderAffairs(v);
          previousOrderAffairs(v);
          return v;
     }

     @Override
     public void onDestroy() {
          super.onDestroy();
          if(valueEventListener != null) databaseReference.child("Delivered or Cancelled").child(authPhoneNumber).removeEventListener(valueEventListener);
          if(valueEventListener2 != null) databaseReference.child("Orders").child(authPhoneNumber).removeEventListener(valueEventListener2);
     }



     private void previousOrderAffairs(final View v) {
          previousOrderProgressBar = v.findViewById(R.id.previousOrdersProgressBar);
          noPreviousOrdersTextView = v.findViewById(R.id.noPreviousOrdersTextView);

          RecyclerView previousOrdersRecyclerView = v.findViewById(R.id.previousOrdersRecyclerView);
          adapter = new RecyclerViewAdapterForPreviousOrders();

          //setting the recycler view
          previousOrdersRecyclerView.setAdapter(adapter);
          previousOrdersRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

          sortingOrderType = sharedPreferences.getString(SORTING_ORDER_TYPE_STORAGE_KEY, SORTING_ORDER_1);

          switch(sortingOrderType) {
               case SORTING_ORDER_1:
                    observeNewestFirst();
                    break;
               case SORTING_ORDER_2:
                    observeOldestFirst();
                    break;
               default:
                    observeNewestFirst();
                    break;
          }




          fetchPreviousOrders(new LocalDatabaseCallbackInterface() {
               @Override
               public void fromOnChildManipulated(ArrayList<CheckoutUser> previousOrdersList) {
                    ArrayList<PreviousOrderEntity> ordersToAddInRoom = new ArrayList<>();
                    for(CheckoutUser u : previousOrdersList){
                         String orderFruitList = "";
                         String status = "";
                         int grandTotal = 0;
                         status = u.getStatus();
                         for(FruitItem f : u.getFruits()){
                              orderFruitList += f.getFruitName() + ", " + f.getFruitQty() + ", " + f.getFruitPrice() + "\n";
                              grandTotal += Integer.valueOf(f.getFruitPrice().replaceAll("[Rs.\\s]", ""));
                         }

                         Long orderPlacedDate =Long.parseLong(u.getOrderPlacedDate());
                         Long orderDeliveredOrCancelledDate = Long.parseLong(u.getOrderDeliveredOrCancelledDate());

                         ordersToAddInRoom.add(new PreviousOrderEntity(orderFruitList, status, "GrandTotal : " + grandTotal + " Rs.", orderPlacedDate, orderDeliveredOrCancelledDate,"false"));
                    }

                    for(PreviousOrderEntity poe : ordersToAddInRoom){
                         previousOrderViewModel.insert(poe);
                    }

                    previousOrderProgressBar.setVisibility(GONE);

                    databaseReference.child("Delivered or Cancelled").child(authPhoneNumber).removeValue();
               }
          });
     }

     private void observeOldestFirst() {
          observer = new Observer<List<PreviousOrderEntity>>() {
               @Override
               public void onChanged(List<PreviousOrderEntity> previousOrderEntities) {
                    if (previousOrderEntities.isEmpty()){
                         noPreviousOrdersTextView.setVisibility(View.VISIBLE);
                    }
                    else {
                         noPreviousOrdersTextView.setVisibility(GONE);
                         handleLongClicks();
                    }
                    adapter.setPreviousOrders(previousOrderEntities);
               }
          };

          previousOrderViewModel.getAllPreviousOrdersOldestFirst().observe(getViewLifecycleOwner(), observer);
     }

     private void observeNewestFirst() {
          observer = new Observer<List<PreviousOrderEntity>>() {
               @Override
               public void onChanged(List<PreviousOrderEntity> previousOrderEntities) {
                    if (previousOrderEntities.isEmpty()){
                         noPreviousOrdersTextView.setVisibility(View.VISIBLE);
                    }
                    else {
                         noPreviousOrdersTextView.setVisibility(GONE);
                         handleLongClicks();
                    }
                    adapter.setPreviousOrders(previousOrderEntities);
               }
          };

          previousOrderViewModel.getAllPreviousOrdersNewestFirst().observe(getViewLifecycleOwner(), observer);
     }

     private void handleLongClicks() {
          adapter.setOnItemClickListener(new RecyclerViewAdapterForPreviousOrders.OnItemClickListener() {
               @Override
               public void onItemTouchHold(int position) {
                    PreviousOrderEntity poe = adapter.getOrderAtPosition(position);
                    String isStarred = poe.getIsStarred();

                    if(isStarred.equals("true")){
                         poe.setIsStarred("false");
                    }
                    else {
                         poe.setIsStarred("true");
                    }
                    previousOrderViewModel.update(poe);

               }
          });
     }

     private void fetchPreviousOrders(final LocalDatabaseCallbackInterface Interface) {

          previousOrderProgressBar.setVisibility(View.VISIBLE);

          valueEventListener = new ValueEventListener() {
               @Override
               public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if(dataSnapshot.exists()){
                         previousOrdersList.clear();

                         for(DataSnapshot data: dataSnapshot.getChildren()){
                              previousOrdersList.add(data.child("Order").getValue(CheckoutUser.class));
                         }

                         Interface.fromOnChildManipulated(previousOrdersList);

                    }
                    else{
                         previousOrderProgressBar.setVisibility(GONE);
                         //no delivered/cancelled orders remaining to read from database
                    }
               }

               @Override
               public void onCancelled(@NonNull DatabaseError databaseError) {
                    handleDatabaseError(databaseError);
               }
          };

          databaseReference.child("Delivered or Cancelled").child(authPhoneNumber).addValueEventListener(valueEventListener);

     }

     private void handleDatabaseError(DatabaseError databaseError) {
          parentLayout.setVisibility(View.INVISIBLE);

          switch(databaseError.getCode()) {
               case DatabaseError.DISCONNECTED :
               case DatabaseError.NETWORK_ERROR :
                    Snackbar mySnackbar = Snackbar.make(parentLayout, "Check your INTERNET Connection", Snackbar.LENGTH_INDEFINITE);
                    mySnackbar.setAction("RETRY", new MyRetryListener());
                    mySnackbar.show();
                    break;
               case DatabaseError.OPERATION_FAILED :
               case DatabaseError.UNKNOWN_ERROR:
                    Snackbar mySnackbar1 = Snackbar.make(parentLayout, "Unknown Error Occurred", Snackbar.LENGTH_INDEFINITE);
                    mySnackbar1.setAction("RETRY", new MyRetryListener());
                    mySnackbar1.show();
                    break;
               case DatabaseError.PERMISSION_DENIED:
                    Snackbar mySnackbar2 = Snackbar.make(parentLayout, "Permission Denied", Snackbar.LENGTH_INDEFINITE);
                    mySnackbar2.setAction("RETRY", new MyRetryListener());
                    mySnackbar2.show();
                    break;
               case DatabaseError.MAX_RETRIES:
                    Snackbar mySnackbar3 = Snackbar.make(parentLayout, "Max tries reached, Try again after some time", Snackbar.LENGTH_INDEFINITE);
                    mySnackbar3.setAction("RETRY", new MyRetryListener());
                    mySnackbar3.show();
                    break;
               default:
                    Snackbar mySnackbar4 = Snackbar.make(parentLayout, "Error Occurred", Snackbar.LENGTH_INDEFINITE);
                    mySnackbar4.setAction("RETRY", new MyRetryListener());
                    mySnackbar4.show();
                    break;
          }
     }

     private void currentOrderAffairs(View v) {
          currentOrderProgressBar = v.findViewById(R.id.currentOrdersProgressBar);
          noCurrentOrdersTextView = v.findViewById(R.id.noCurrentOrdersTextView);

          currentOrdersRecyclerView = v.findViewById(R.id.currentOrdersRecyclerView);

          fetchCurrentOrders(new DatabaseCallbackInterface() {
               @Override
               public void fromOnChildManipulated(ArrayList<CheckoutUser> currentOrdersList) {
                    if(currentOrdersList.isEmpty()){
                         noCurrentOrdersTextView.setVisibility(View.VISIBLE);
                    }
                    else {
                         currentOrdersRecyclerView.setVisibility(View.VISIBLE);
                         adapterForCurrentOrders = new RecyclerViewAdapterForCurrentOrders(getContext(), currentOrdersList);
                         currentOrdersRecyclerView.setAdapter(adapterForCurrentOrders);
                         currentOrdersRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                         noCurrentOrdersTextView.setVisibility(GONE);

                         handleCurrentOrderButtonClicks();
                    }

                    currentOrderProgressBar.setVisibility(GONE);
               }
          });
     }

     private void handleCurrentOrderButtonClicks() {
          adapterForCurrentOrders.setOnButtonClickListener(new RecyclerViewAdapterForCurrentOrders.onButtonClickListener() {
               @Override
               public void onButtonClick(final int position) {
                    CancelCurrentOrderDialog dialog1 = new CancelCurrentOrderDialog(position);
                    dialog1.setTargetFragment(PreviousOrdersFragment.this, 0);
                    dialog1.show(getParentFragmentManager(), "cancelling order at position " + position);
               }
          });
     }

     @Override
     public void onDialogPositiveClick(int position) {
          CheckoutUser currentOrderDetails =  currentOrdersList.get(position);
          currentOrderDetails.setStatus("CANCELLATION REQUESTED");
          currentOrderProgressBar.setVisibility(View.VISIBLE);
          String firebaseKey = currentOrderDetails.getFirebaseDatabaseKey();

          Map<String, Object> map = new HashMap<>();
          map.put( "/Orders/" + authPhoneNumber + "/" + firebaseKey, currentOrderDetails);
          map.put("/tokens/" + authPhoneNumber, token);

          databaseReference.updateChildren(map).addOnSuccessListener(new OnSuccessListener<Void>() {
               @Override
               public void onSuccess(Void aVoid) {
                    currentOrderProgressBar.setVisibility(GONE);
                    Toast.makeText(getContext(), "Requested cancellation successfully", Toast.LENGTH_SHORT).show();
               }
          }).addOnFailureListener(new OnFailureListener() {
               @Override
               public void onFailure(@NonNull Exception e) {
                    currentOrderProgressBar.setVisibility(GONE);
                    Toast.makeText(getContext(), "Something went wrong! try again", Toast.LENGTH_SHORT).show();
               }
          });
     }

     private void fetchCurrentOrders(final DatabaseCallbackInterface Interface) {

          currentOrderProgressBar.setVisibility(View.VISIBLE);

          valueEventListener2 = new ValueEventListener() {
               @Override
               public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    currentOrderProgressBar.setVisibility(View.VISIBLE);
                    currentOrdersList.clear();
                    for(DataSnapshot data : dataSnapshot.getChildren()){
                         currentOrdersList.add(data.getValue(CheckoutUser.class));
                    }

                    Interface.fromOnChildManipulated(currentOrdersList);
               }

               @Override
               public void onCancelled(@NonNull DatabaseError databaseError) {
                    handleDatabaseError(databaseError);
               }
          };

          databaseReference.child("Orders").child(authPhoneNumber).addValueEventListener(valueEventListener2);


     }

     @Override
     public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
          inflater.inflate(R.menu.options_menu_previous_orders_fragment, menu);
     }

     @Override
     public boolean onOptionsItemSelected(@NonNull MenuItem item) {

          SharedPreferences.Editor editor = sharedPreferences.edit();

          switch(item.getItemId()){

               case R.id.sort_by_latest_first:
                    if(!sortingOrderType.equals(SORTING_ORDER_1)) {
                         editor.putString(SORTING_ORDER_TYPE_STORAGE_KEY, SORTING_ORDER_1);
                         editor.apply();
                         // remove observer of current sorting type by if else chain
                         if(sortingOrderType.equals(SORTING_ORDER_2)){
                              //then remove the observer for SORTING_ORDER_2
                              //previousOrderViewModel.getAllPreviousOrdersOldestFirst().removeObserver(observerForOldestFirst);
                              LiveData<List<PreviousOrderEntity>> observable = previousOrderViewModel.getAllPreviousOrdersOldestFirst();
                              observable.removeObserver(observer);
                         }
                         //else if (sortingOrderType.equals(some other sorting type)){}

                         observeNewestFirst();
                         sortingOrderType = SORTING_ORDER_1;
                    }
                    return true;
               case R.id.sort_by_oldest_first:
                    if(!sortingOrderType.equals(SORTING_ORDER_2)) {
                         editor.putString(SORTING_ORDER_TYPE_STORAGE_KEY, SORTING_ORDER_2);
                         editor.apply();

                         // remove observer of current sorting type by if else chain
                         if(sortingOrderType.equals(SORTING_ORDER_1)){
                              //then remove the observer for SORTING_ORDER_2
                              //previousOrderViewModel.getAllPreviousOrdersOldestFirst().removeObserver(observerForNewestFirst);
                              LiveData<List<PreviousOrderEntity>> observable = previousOrderViewModel.getAllPreviousOrdersNewestFirst();
                              observable.removeObserver(observer);
                         }
                         //else if (sortingOrderType.equals(some other sorting type)){}

                         observeOldestFirst();
                         sortingOrderType = SORTING_ORDER_2;
                    }
                    return true;
               case R.id.delete_all_previous_orders:
                    DeletePreviousOrdersDialog dialog = new DeletePreviousOrdersDialog();
                    dialog.setTargetFragment(this, 0);
                    dialog.show(getParentFragmentManager(), "delete previous orders from the storage");
                    return true;
               default:
                    return super.onOptionsItemSelected(item);
          }

     }

     @Override
     public void onDialogPositiveClick(boolean keepStarredOrders) {
          if(keepStarredOrders){
               previousOrderViewModel.deleteAllNotStarredPreviousOrders();
          }
          else {
               previousOrderViewModel.deleteAllPreviousOrders();
          }
     }

     private class MyRetryListener implements View.OnClickListener {
          @Override
          public void onClick(View v) {
               //recreate the fragment
               PreviousOrdersFragment fragment = (PreviousOrdersFragment) getParentFragmentManager().findFragmentById(R.id.nav_host_fragment);
               getParentFragmentManager().beginTransaction()
                       .detach(fragment)
                       .attach(fragment)
                       .commit();
          }
     }
}
