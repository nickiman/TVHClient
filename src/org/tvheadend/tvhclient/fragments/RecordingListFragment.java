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
import org.tvheadend.tvhclient.PlaybackSelectionActivity;
import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.Utils;
import org.tvheadend.tvhclient.adapter.RecordingListAdapter;
import org.tvheadend.tvhclient.htsp.HTSService;
import org.tvheadend.tvhclient.intent.SearchEPGIntent;
import org.tvheadend.tvhclient.intent.SearchIMDbIntent;
import org.tvheadend.tvhclient.interfaces.FragmentControlInterface;
import org.tvheadend.tvhclient.interfaces.FragmentStatusInterface;
import org.tvheadend.tvhclient.interfaces.HTSListener;
import org.tvheadend.tvhclient.model.Recording;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class RecordingListFragment extends Fragment implements HTSListener, FragmentControlInterface {

    public static String TAG = RecordingListFragment.class.getSimpleName();

    protected Activity activity;
    protected FragmentStatusInterface fragmentStatusInterface;
    protected RecordingListAdapter adapter;
    private ListView listView;

    // This is the default view for the channel list adapter. Other views can be
    // passed to the adapter to show less information. This is used in the
    // program guide where only the channel icon is relevant.
    private int adapterLayout = R.layout.recording_list_widget;

    protected boolean isDualPane;

    protected Toolbar toolbar;

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
            adapterLayout = R.layout.recording_list_widget_dual_pane;
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

        adapter = new RecordingListAdapter(activity, new ArrayList<Recording>(), adapterLayout);
        listView.setAdapter(adapter);

        // Set the listener to show the recording details activity when the user
        // has selected a recording
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Recording rec = (Recording) adapter.getItem(position);
                if (fragmentStatusInterface != null) {
                    fragmentStatusInterface.onListItemSelected(position, rec, TAG);
                }
                adapter.setPosition(position);
                adapter.notifyDataSetChanged();
            }
        });

        registerForContextMenu(listView);

        if (toolbar != null) {
            toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return onToolbarItemSelected(item);
                }
            });
            // Inflate a menu to be displayed in the toolbar
            toolbar.inflateMenu(R.menu.recording_menu);

            toolbar.setNavigationIcon(R.drawable.ic_launcher);
            if (!isDualPane) {
                // Allow clicking on the navigation icon, if available. The icon is
                // set in the populateTagList method
                toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        activity.onBackPressed();
                    }
                });
            }
        }
    }

    @Override
    public void onDetach() {
        fragmentStatusInterface = null;
        super.onDetach();
    }

    /**
     * 
     * @param item
     * @return
     */
    private boolean onToolbarItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_play:
            // Open a new activity that starts playing the selected recording
            Recording rec = adapter.getSelectedItem();
            if (rec != null) {
                Intent intent = new Intent(activity, PlaybackSelectionActivity.class);
                intent.putExtra(Constants.BUNDLE_RECORDING_ID, rec.id);
                startActivity(intent);
            }
            return true;

        case R.id.menu_record_remove:
            Utils.confirmRemoveRecording(activity, adapter.getSelectedItem());
            return true;

        case R.id.menu_record_remove_all:
            // Show a confirmation dialog before deleting all recordings
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.menu_record_remove_all)
                    .setMessage(getString(R.string.delete_all_recordings))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            removeAllRecordings();
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // NOP
                        }
                    }).show();
            return true;

        case R.id.menu_record_cancel:
            Utils.confirmCancelRecording(activity, adapter.getSelectedItem());
            return true;

        case R.id.menu_record_cancel_all:
            // Show a confirmation dialog before canceling all recordings
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.menu_record_cancel_all)
                    .setMessage(getString(R.string.cancel_all_recordings))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            cancelAllRecordings();
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // NOP
                        }
                    }).show();
            return true;

        case R.id.menu_refresh:
            fragmentStatusInterface.reloadData(TAG);
            return true;

        default:
            return false;
        }
    }

    /**
     * Calls the service to cancel the scheduled recordings. The service is
     * called in a certain interval to prevent too many calls to the interface.
     */
    private void cancelAllRecordings() {
        new Thread() {
            public void run() {
                for (int i = 0; i < adapter.getCount(); ++i) {
                    Utils.cancelRecording(activity, adapter.getItem(i));
                    try {
                        sleep(Constants.THREAD_SLEEPING_TIME);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Error cancelling all recordings, " + e.getLocalizedMessage());
                    }
                }
            }
        }.start();
    }

    /**
     * Calls the service to remove the scheduled recordings. The service is
     * called in a certain interval to prevent too many calls to the interface.
     */
    private void removeAllRecordings() {
        new Thread() {
            public void run() {
                for (int i = 0; i < adapter.getCount(); ++i) {
                    final Recording rec = adapter.getItem(i);
                    if (rec != null) {
                        final Intent intent = new Intent(activity, HTSService.class);
                        intent.setAction(Constants.ACTION_DELETE_DVR_ENTRY);
                        intent.putExtra("id", rec.id);
                        activity.startService(intent);
                    }
                    try {
                        sleep(Constants.THREAD_SLEEPING_TIME);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Error removing all recordings, " + e.getLocalizedMessage());
                    }
                }
            }
        }.start();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        activity.getMenuInflater().inflate(R.menu.recording_context_menu, menu);

        // Get the currently selected program from the list where the context
        // menu has been triggered
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Recording rec = adapter.getItem(info.position);

        // Set the title of the context menu and show or hide 
        // the menu items depending on the recording state
        menu.setHeaderTitle(rec.title);

        // Get the menu items so they can be shown 
        // or hidden depending on the recording state
        MenuItem recordCancelMenuItem = menu.findItem(R.id.menu_record_cancel);
        MenuItem recordRemoveMenuItem = menu.findItem(R.id.menu_record_remove);
        MenuItem playMenuItem = menu.findItem(R.id.menu_play);
        MenuItem searchMenuItemEpg = menu.findItem(R.id.menu_search_epg);
        MenuItem searchMenuItemImdb = menu.findItem(R.id.menu_search_imdb);

        // Disable these menus as a default
        recordCancelMenuItem.setVisible(false);
        recordRemoveMenuItem.setVisible(false);
        playMenuItem.setVisible(false);
        searchMenuItemEpg.setVisible(false);
        searchMenuItemImdb.setVisible(false);

        // Exit if the recording is not valid
        if (rec != null) {
            // Allow searching the recordings
            searchMenuItemEpg.setVisible(true);
            searchMenuItemImdb.setVisible(true);
    
            if (rec.error == null && rec.state.equals("completed")) {
                // The recording is available, it can be played and removed
                recordRemoveMenuItem.setVisible(true);
                playMenuItem.setVisible(true);
            } else if (rec.isRecording() || rec.isScheduled()) {
                // The recording is recording or scheduled, it can only be cancelled
                recordCancelMenuItem.setVisible(true);
            } else if (rec.error != null || rec.state.equals("missed")) {
                // The recording has failed or has been missed, allow removal
                recordRemoveMenuItem.setVisible(true);
            }
        }
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

        // Check for a valid adapter size and objects
        if (info == null || adapter == null || adapter.getCount() <= info.position) {
            return super.onContextItemSelected(item);
        }

        final Recording rec = adapter.getItem(info.position);

        switch (item.getItemId()) {
        case R.id.menu_search_imdb:
            startActivity(new SearchIMDbIntent(activity, rec.title));
            return true;

        case R.id.menu_search_epg:
            startActivity(new SearchEPGIntent(activity, rec.title));
            return true;

        case R.id.menu_record_remove:
            Utils.confirmRemoveRecording(activity, rec);
            return true;

        case R.id.menu_record_cancel:
            Utils.confirmCancelRecording(activity, rec);
            return true;

        case R.id.menu_play:
            Intent intent = new Intent(activity, PlaybackSelectionActivity.class);
            intent.putExtra(Constants.BUNDLE_RECORDING_ID, rec.id);
            startActivity(intent);
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
        if (action.equals(Constants.ACTION_DVR_ADD)) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    adapter.add((Recording) obj);
                }
            });
        } else if (action.equals(Constants.ACTION_DVR_DELETE)) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    // Get the position of the recording that is shown before
					// the one that has been deleted. This recording will then
					// be selected when the list has been updated.
                    int previousPosition = adapter.getPosition((Recording) obj);
                    if (--previousPosition < 0) {
                        previousPosition = 0;
                    }
                    adapter.remove((Recording) obj);
                    // Set the recording below the deleted one as selected
                    setInitialSelection(previousPosition);
                }
            });
        } else if (action.equals(Constants.ACTION_DVR_UPDATE)) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    adapter.update((Recording) obj);
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
        if (adapter != null) {
            Recording recording = null;
            if (adapter.getCount() > position) {
                adapter.setPosition(position);
                recording = (Recording) adapter.getItem(position);
            }
            
            // Simulate a click in the list item to inform the activity
            // It will then show the details fragment if dual pane is active
            // If the recording is null pass it on anyway.
            if (isDualPane) {
                if (fragmentStatusInterface != null) {
                    fragmentStatusInterface.onListItemSelected(position, recording, TAG);
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
