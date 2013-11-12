package com.ese2013.mub;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.Toast;

import com.ese2013.mub.model.Mensa;
import com.ese2013.mub.model.Model;
import com.ese2013.mub.util.DirectionsDownloadTask;
import com.ese2013.mub.util.NamedLocation;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLngBounds;

/**
 * Displays a map showing all the Mensas and, if possible, the location of the
 * user. Allows to display the path to a specific Mensa.
 * 
 * At startup the closest Mensa is selected if possible, else a favorite Mensa
 * if one exists. If no closest or favorite Mensa are available, just the first
 * Mensa in the Mensa List is selected.
 * 
 */
public class MapFragment extends Fragment {
	private static final String TRAVEL_MODE_WALKING = "walking", TRAVEL_MODE_BICYCLE = "bicycle",
			TRAVEL_MODE_DRIVING = "driving";
	public static final String MENSA_ID_LOCATION = "mensa.id";
	private GoogleMap map;
	private ArrayList<NamedLocation> namedLocations = new ArrayList<NamedLocation>();
	private ArrayAdapter<NamedLocation> namedLocationsAdapter;
	private NamedLocation currentLocation;
	private NamedLocation selectedLocation;
	private LocationManager locationManager;
	private Spinner locationSpinner;
	private Model model;
	private boolean drawPath;
	private String travelMode = TRAVEL_MODE_WALKING;

	/**
	 * Sets up the map view, listeners and default values.
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_map, container, false);
		locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
		map = ((SupportMapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

		if (map == null)
			return view;

		model = Model.getInstance();
		setLocationListener();
		setRadioGroupListener(view);

		ImageButton getDirButton = (ImageButton) view.findViewById(R.id.get_directions_button);
		setDirectionsButtonListener(getDirButton);
		getDirButton.setImageResource(R.drawable.ic_action_directions);

		namedLocationsAdapter = new ArrayAdapter<NamedLocation>(getActivity(),
				android.R.layout.simple_spinner_dropdown_item, namedLocations);
		namedLocations.addAll(createNamedLocationsFromMensas());

		locationSpinner = (Spinner) view.findViewById(R.id.focus_spinner);
		locationSpinner.setAdapter(namedLocationsAdapter);

		Location rawLocation = getLocation();
		if (rawLocation != null)
			updateCurrentLocation(rawLocation);

		setSpinnerItemSelectionListener(locationSpinner);
		setSpinnerDefault();

		return view;
	}

	/**
	 * Is called after view was created. We check here the passed bundle for a
	 * Mensa (represented by id) to select and draw the path to.
	 */
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		Bundle bundle = getArguments();
		if (bundle == null || bundle.isEmpty()) {
			repaintMap();
			return;
		}

		Integer mensaId = (Integer) bundle.get(MENSA_ID_LOCATION);
		if (mensaId != null) {
			for (NamedLocation nl : namedLocations) {
				if (nl.isLocationOfMensa(mensaId.intValue())) {
					setSpinnerTo(nl);
					if (currentLocationAvailable())
						onClickDirectionsButton(getView().findViewById(R.id.get_directions_button));
				}
			}
		}
		repaintMap();
	}

	/**
	 * Sets the spinner to his default value. If possible, it is set to the
	 * closest mensa. Else to a favorite Mensa if one exists. If there is no
	 * closest Mensa and no favorite Mensa, just the first Mensa in the Mensa
	 * List is selected.
	 */
	private void setSpinnerDefault() {
		if (model.noMensasLoaded())
			return;

		Mensa selectedMensa;
		if (currentLocationAvailable())
			selectedMensa = getClosestMensa(currentLocation);
		else
			selectedMensa = model.favoritesExist() ? model.getFavoriteMensas().get(0) : model.getMensas().get(0);

		for (NamedLocation namedLoc : namedLocations)
			if (namedLoc.isLocationOfMensa(selectedMensa))
				selectedLocation = namedLoc;

		setSpinnerTo(selectedLocation);
	}

	private void setDirectionsButtonListener(ImageButton button) {
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onClickDirectionsButton(v);
			}
		});
	}

	private void onClickDirectionsButton(View view) {
		if (currentLocationAvailable()) {
			drawPath = !drawPath;
			ImageButton getDirButton = (ImageButton) view;
			getDirButton.setImageResource(drawPath ? R.drawable.ic_action_directions_active
					: R.drawable.ic_action_directions);
			repaintMap();
		}
	}

	private void setRadioGroupListener(View view) {
		RadioGroup radioGroup = (RadioGroup) view.findViewById(R.id.rg_modes);
		radioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				RadioButton radioButton = (RadioButton) getView().findViewById(checkedId);
				switch (radioButton.getId()) {
				case R.id.rb_bicycling:
					travelMode = TRAVEL_MODE_BICYCLE;
					break;
				case R.id.rb_walking:
					travelMode = TRAVEL_MODE_WALKING;
					break;
				case R.id.rb_driving:
					travelMode = TRAVEL_MODE_DRIVING;
					break;
				}
				repaintMap();
			}
		});
	}

	private void setSpinnerItemSelectionListener(Spinner spinFocus) {
		spinFocus.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				NamedLocation namedLoc = (NamedLocation) parent.getItemAtPosition(position);
				selectedLocation.resetColor();
				selectedLocation = namedLoc;
				selectedLocation.setColorSelected();
				repaintMap();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

	}

	/**
	 * Sets up the Location Listener. The listener is used to update the current
	 * location if the location of the phone has been changed.
	 */
	private void setLocationListener() {
		String provider = locationManager.getBestProvider(new Criteria(), true);
		LocationListener locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				updateCurrentLocation(location);
			}

			@Override
			public void onProviderDisabled(String arg0) {
			}

			@Override
			public void onProviderEnabled(String arg0) {
				updateCurrentLocation(getLocation());
			}

			@Override
			public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
			}
		};
		locationManager.requestLocationUpdates(provider, 1000, 15, locationListener);
	}

	/**
	 * Updates the current location and redraws the map.
	 * 
	 * @param location
	 *            the new current Location. Must not be null.
	 */
	private void updateCurrentLocation(Location location) {
		if (currentLocation == null)
			currentLocation = new NamedLocation(location, getActivity().getString(R.string.map_my_location),
					BitmapDescriptorFactory.HUE_AZURE);
		else
			currentLocation.setLocation(location);

		repaintMap();
	}

	/**
	 * Sets the spinner to a given NamedLocation
	 * 
	 * @param location
	 *            NamedLocation for the spinner to be set to. Must be in the
	 *            namedLocationsAdapter.
	 */
	private void setSpinnerTo(NamedLocation location) {
		locationSpinner.setSelection(namedLocationsAdapter.getPosition(location));
	}

	/**
	 * Creates a List of NamedLocations representing the Mensas.
	 * 
	 * @return List of NamedLocations.
	 */
	private List<NamedLocation> createNamedLocationsFromMensas() {
		ArrayList<NamedLocation> results = new ArrayList<NamedLocation>();
		for (Mensa m : model.getMensas())
			results.add(new NamedLocation(m));
		return results;
	}

	/**
	 * Repaints the map, also updates the displayed path to a Mensa if needed
	 * and the zoom.
	 */
	private void repaintMap() {
		map.clear();
		drawAllLocations();
		if (model.mensasLoaded()) {
			if (drawPath)
				drawRouteFromTo(currentLocation, selectedLocation);
			zoomOnContent();
		}
	}

	/**
	 * Sets the zoom to either show all Mensas or, if the path is drawn, to just
	 * show the current location and the target Mensa.
	 */
	private void zoomOnContent() {
		if (drawPath)
			zoomOnContent(currentLocation, selectedLocation);
		else
			zoomOnContent(namedLocations);
	}

	private void zoomOnContent(NamedLocation loc1, NamedLocation loc2) {
		List<NamedLocation> list = new ArrayList<NamedLocation>();
		list.add(loc1);
		list.add(loc2);
		zoomOnContent(list);
	}

	private void zoomOnContent(List<NamedLocation> locations) {
		LatLngBounds.Builder bounds = new LatLngBounds.Builder();
		for (NamedLocation l : locations)
			bounds.include(l.getLatLng());
		if (currentLocation != null)
			bounds.include(currentLocation.getLatLng());
		map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 400, 400, 15));
	}

	/**
	 * Calculates and draws the path from the given origin to destination using
	 * the callback method onDirectionsDownloadFinished
	 * 
	 * @param origin
	 *            Start NamedLocation. Must not be null.
	 * @param destination
	 *            Goal NamedLocation. Must not be null.
	 */
	private void drawRouteFromTo(NamedLocation origin, NamedLocation destination) {
		DirectionsDownloadTask downloadTask = new DirectionsDownloadTask(origin.getLatLng(), destination.getLatLng(),
				travelMode, this);
		downloadTask.execute();
	}

	/**
	 * Callback from DirectionsDownloadTask.
	 * 
	 * @param downloadTask
	 *            the Task which downloaded the directions.
	 */
	public void onDirectionsDownloadFinished(DirectionsDownloadTask downloadTask) {
		if (downloadTask.wasSuccesful())
			map.addPolyline(downloadTask.getPolyline());
		else
			showDirectionsError();
	}

	private void showDirectionsError() {
		Toast.makeText(getActivity(), getActivity().getString(R.string.map_directions_error), Toast.LENGTH_LONG).show();
	}

	private boolean currentLocationAvailable() {
		return currentLocation != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
	}

	private void drawAllLocations() {
		for (NamedLocation n : namedLocations)
			map.addMarker(n.getMarker());
		if (currentLocation != null)
			map.addMarker(currentLocation.getMarker());
	}

	/**
	 * Returns the Mensa which is closest to the given Location.
	 * 
	 * @param location
	 *            Location from which the closest Mensa is searched.
	 * @return Mensa which is closest to the given Location.
	 */
	private Mensa getClosestMensa(Location location) {
		Mensa result = null;
		float smallestDist = Integer.MAX_VALUE;
		for (Mensa m : model.getMensas()) {
			Location mensaLocation = new Location("");
			mensaLocation.setLatitude(m.getLatitude());
			mensaLocation.setLongitude(m.getLongitude());
			float currentDist = location.distanceTo(mensaLocation);
			if (smallestDist > currentDist) {
				smallestDist = currentDist;
				result = m;
			}
		}
		return result;
	}

	private Location getLocation() {
		return locationManager.getLastKnownLocation(locationManager.getBestProvider(new Criteria(), true));
	}

	/**
	 * Destroy method which is called by the android framework when the view is
	 * no longer needed. Here we remove the google map fragment which is
	 * embedded in our own map fragment.
	 */
	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (!getActivity().isChangingConfigurations())
			removeMapFragment();
	}

	private void removeMapFragment() {
		FragmentManager fm = getActivity().getSupportFragmentManager();
		Fragment fragment = fm.findFragmentById(R.id.map);
		if (fragment != null)
			fm.beginTransaction().remove(fragment).commitAllowingStateLoss();
	}
}