/*
 *  Copyright (C) 2013 Robert Siebert
 *  Copyright (C) 2011 John Törnblom
 *
 * This file is part of TVHGuide.
 *
 * TVHGuide is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TVHGuide is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TVHGuide.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tvheadend.tvhclient.fragments;

import java.util.ArrayList;

import org.tvheadend.tvhclient.Constants;
import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.TVHClientApplication;
import org.tvheadend.tvhclient.Utils;
import org.tvheadend.tvhclient.adapter.SeriesRecordingListAdapter;
import org.tvheadend.tvhclient.intent.SearchEPGIntent;
import org.tvheadend.tvhclient.intent.SearchIMDbIntent;
import org.tvheadend.tvhclient.interfaces.FragmentControlInterface;
import org.tvheadend.tvhclient.interfaces.FragmentStatusInterface;
import org.tvheadend.tvhclient.interfaces.HTSListener;
import org.tvheadend.tvhclient.model.SeriesRecording;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class SeriesRecordingListFragment extends Fragment implements HTSListener, FragmentControlInterface {

    public static String TAG = SeriesRecordingListFragment.class.getSimpleName();

    protected Activity activity;
    protected FragmentStatusInterface fragmentStatusInterface;
    protected SeriesRecordingListAdapter adapter;
    private ListView listView;

    // This is the default view for the channel list adapter. Other views can be
    // passed to the adapter to show less information. This is used in the
    // program guide where only the channel icon is relevant.
    private int adapterLayout = R.layout.series_recording_list_widget;

    protected boolean isDualPane;

    private Toolbar toolbar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        // Return if frame for this fragment doesn't exist because the fragment
        // will not be shown.
        if (container == null) {
            return null;
        }

        // Get the passed argument so we know which recording type to display
        Bundle bundle = getArguments();
        if (bundle != null) {
            isDualPane  = bundle.getBoolean(Constants.BUNDLE_DUAL_PANE, false);
        }
        if (isDualPane) {
            adapterLayout = R.layout.series_recording_list_widget_dual_pane;
        }

        View v = inflater.inflate(R.layout.list_layout, container, false);
        listView = (ListView) v.findViewById(R.id.item_list);
        toolbar = (Toolbar) v.findViewById(R.id.toolbar);
        return v;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (activity instanceof FragmentStatusInterface) {
            fragmentStatusInterface = (FragmentStatusInterface) activity;
        }

        adapter = new SeriesRecordingListAdapter(activity, new ArrayList<SeriesRecording>(), adapterLayout);
        listView.setAdapter(adapter);

        // Set the listener to show the recording details activity when the user
        // has selected a recording
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SeriesRecording srec = (SeriesRecording) adapter.getItem(position);
                if (fragmentStatusInterface != null) {
                    fragmentStatusInterface.onListItemSelected(position, srec, TAG);
                }
                adapter.setPosition(position);
                adapter.notifyDataSetChanged();
            }
        });

        setHasOptionsMenu(true);
        registerForContextMenu(listView);
    }

    @Override
    public void onDetach() {
        fragmentStatusInterface = null;
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        TVHClientApplication app = (TVHClientApplication) activity.getApplication();
        app.addListener(this);
        if (!app.isLoading()) {
            populateList();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        TVHClientApplication app = (TVHClientApplication) activity.getApplication();
        app.removeListener(this);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // Only show the cancel recording menu when in dual pane mode. Only
        // there a recording is preselected. In single mode the first recording
        // would always be preselected. 
        if (!isDualPane) {
            (menu.findItem(R.id.menu_record_remove)).setVisible(false);
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (prefs.getBoolean("hideMenuDeleteAllRecordingsPref", false)) {
            (menu.findItem(R.id.menu_record_remove_all)).setVisible(false);
        }

        (menu.findItem(R.id.menu_record_cancel)).setVisible(false);
        (menu.findItem(R.id.menu_record_cancel_all)).setVisible(false);
        // Playing a failed recording is not possible
        (menu.findItem(R.id.menu_play)).setVisible(false);
    }

    /**
     * Fills the list with the available recordings. Only the recordings that
     * are scheduled are added to the list.
     */
    private void populateList() {
        // Clear the list and add the recordings
        adapter.clear();
        TVHClientApplication app = (TVHClientApplication) activity.getApplication();
        for (SeriesRecording srec : app.getSeriesRecordings()) {
            adapter.add(srec);
        }
        // Show the newest scheduled recordings first 
        adapter.sort(Constants.RECORDING_SORT_DESCENDING);
        adapter.notifyDataSetChanged();
        
        // Shows the currently visible number of recordings of the type  
        if (toolbar != null) {
            toolbar.setTitle(getString(R.string.recordings));
            toolbar.setSubtitle(adapter.getCount() + " " + getString(R.string.upcoming_recordings));
            toolbar.setNavigationIcon(R.drawable.ic_launcher);
        }
        // Inform the listeners that the channel list is populated.
        // They could then define the preselected list item.
        if (fragmentStatusInterface != null) {
            fragmentStatusInterface.onListPopulated(TAG);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_record_remove:
            Utils.confirmRemoveRecording(activity, null, adapter.getSelectedItem());
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.recording_menu, menu);
        Utils.setRecordingMenuIcons(activity, menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        activity.getMenuInflater().inflate(R.menu.series_recording_context_menu, menu);

        // Get the currently selected program from the list where the context
        // menu has been triggered
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        SeriesRecording srec = adapter.getItem(info.position);
        menu.setHeaderTitle(srec.title);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The context menu is triggered for all fragments that are in a
        // fragment pager. Do nothing for invisible fragments.
        if (!getUserVisibleHint()) {
            return super.onContextItemSelected(item);
        }
        // Get the currently selected program from the list where the context
        // menu has been triggered
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        SeriesRecording srec = adapter.getItem(info.position);

        switch (item.getItemId()) {
        case R.id.menu_search_imdb:
            startActivity(new SearchIMDbIntent(activity, srec.title));
            return true;

        case R.id.menu_search_epg:
            startActivity(new SearchEPGIntent(activity, srec.title));
            return true;

        case R.id.menu_record_remove:
            Utils.confirmRemoveRecording(activity, null, srec);
            return true;

        default:
            return super.onContextItemSelected(item);
        }
    }

    /**
     * This method is part of the HTSListener interface. Whenever the HTSService
     * sends a new message the specified action will be executed here.
     */
    @Override
    public void onMessage(String action, final Object obj) {
        if (action.equals(Constants.ACTION_LOADING)) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    boolean loading = (Boolean) obj;
                    if (loading) {
                        adapter.clear();
                        adapter.notifyDataSetChanged();
                    } else {
                        populateList();
                    }
                }
            });
        } else if (action.equals(Constants.ACTION_SERIES_DVR_ADD)) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    adapter.add((SeriesRecording) obj);
                    adapter.notifyDataSetChanged();
                }
            });
        } else if (action.equals(Constants.ACTION_SERIES_DVR_DELETE)) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    // Get the position of the series recording that has been deleted
                    int previousPosition = adapter.getPosition((SeriesRecording) obj);
                    adapter.remove((SeriesRecording) obj);
                    // Set the series recording below the deleted one as selected
                    setInitialSelection(previousPosition);
                    adapter.notifyDataSetChanged();
                }
            });
        } else if (action.equals(Constants.ACTION_SERIES_DVR_UPDATE)) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    adapter.update((SeriesRecording) obj);
                    adapter.notifyDataSetChanged();
                }
            });
        }
    }

    @Override
    public void reloadData() {
        // NOP
    }

    @Override
    public void setSelection(int position, int index) {
        if (listView != null && listView.getCount() > position && position >= 0) {
            listView.setSelectionFromTop(position, index);
        }
    }

    @Override
    public void setInitialSelection(int position) {
        setSelection(position, 0);

        // Set the position in the adapter so that we can show the selected
        // recording in the theme with the arrow.
        if (adapter != null && adapter.getCount() > position) {
            adapter.setPosition(position);

            // Simulate a click in the list item to inform the activity
            // It will then show the details fragment if dual pane is active
            if (isDualPane) {
                SeriesRecording srec = (SeriesRecording) adapter.getItem(position);
                if (fragmentStatusInterface != null) {
                    fragmentStatusInterface.onListItemSelected(position, srec, TAG);
                }
            }
        }
    }

    @Override
    public Object getSelectedItem() {
        return adapter.getSelectedItem();
    }

    @Override
    public int getItemCount() {
        return adapter.getCount();
    }
}
