package com.hyphenate.chatuidemo.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.hyphenate.EMCallBack;
import com.hyphenate.EMValueCallBack;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMGroup;
import com.hyphenate.chat.EMMucShareFile;
import com.hyphenate.chatuidemo.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SharedFilesActivity extends BaseActivity {

    private static final int REQUEST_CODE_SELECT_FILE = 1;
    private ListView listView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private int pageSize = 50;
    private int pageNum = 1;

    private String groupId;
    EMGroup group;
    private List<EMMucShareFile> fileList;

    private FilesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_files);


        groupId = getIntent().getStringExtra("groupId");
        group = EMClient.getInstance().groupManager().getGroup(groupId);

        fileList = new ArrayList<>();

        listView = (ListView) findViewById(R.id.list_view);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_layout);
        registerForContextMenu(listView);

        showFileList(true);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                pageNum = 1;
                showFileList(true);
            }
        });

    }

    private void showFileList(boolean isRefresh) {
        swipeRefreshLayout.setRefreshing(true);
        EMClient.getInstance().groupManager().asyncFetchGroupShareFiles(groupId, pageNum, pageSize, new EMValueCallBack<List<EMMucShareFile>>() {
            @Override
            public void onSuccess(final List<EMMucShareFile> value) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        swipeRefreshLayout.setRefreshing(false);
                        fileList.clear();
                        fileList.addAll(value);
                        if(adapter == null){
                            adapter = new FilesAdapter(SharedFilesActivity.this, 1, fileList);
                            listView.setAdapter(adapter);
                        }else{
                            adapter.notifyDataSetChanged();
                        }

                    }
                });
            }

            @Override
            public void onError(int error, String errorMsg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(SharedFilesActivity.this, "Load files fail", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add("Delete File");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Deleting...");
        pd.setCanceledOnTouchOutside(false);
        pd.show();

        final int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;

        EMClient.getInstance().groupManager().asyncDeleteGroupShareFile(
                groupId,
                fileList.get(position).getFileId(),
                new EMCallBack() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.dismiss();
                                fileList.remove(position);
                                adapter.notifyDataSetChanged();

                            }
                        });
                    }

                    @Override
                    public void onError(int code, final String error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.dismiss();
                                Toast.makeText(SharedFilesActivity.this, "Delete file fails, " + error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onProgress(int progress, String status) {
                    }
                }
        );

        return super.onContextItemSelected(item);
    }

    /**
     * upload file button clicked
     * @param view
     */
    public void uploadFile(View view){
        selectFileFromLocal();
    }

    /**
     * select file
     */
    protected void selectFileFromLocal() {
        Intent intent = null;
        if (Build.VERSION.SDK_INT < 19) { //api 19 and later, we can't use this way, demo just select from images
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

        } else {
            intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        startActivityForResult(intent, REQUEST_CODE_SELECT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK){
            if(requestCode == REQUEST_CODE_SELECT_FILE){
                if (data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        uploadFileWithUri(uri);
                    }
                }
            }
        }
    }

    private void uploadFileWithUri(Uri uri) {
        String filePath = getFilePath(uri);
        if (filePath == null) return;
        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(this, R.string.File_does_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }
        //limit the size < 10M
        if (file.length() > 10 * 1024 * 1024) {
            Toast.makeText(this, R.string.The_file_is_not_greater_than_10_m, Toast.LENGTH_SHORT).show();
            return;
        }
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setCanceledOnTouchOutside(false);
        pd.setMessage("Uploading...");
        pd.show();
        EMClient.getInstance().groupManager().asyncUploadGroupShareFile(groupId, filePath, new EMCallBack() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();
                        if(adapter != null){
                            fileList.clear();
                            fileList.addAll(group.getShareFileList());
                            adapter.notifyDataSetChanged();
                            Toast.makeText(SharedFilesActivity.this, "Upload success", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            }

            @Override
            public void onError(int code, final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();
                        Toast.makeText(SharedFilesActivity.this, "Upload fail, " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override
            public void onProgress(int progress, String status) {
            }
        });
    }

    @Nullable
    private String getFilePath(Uri uri) {
        String filePath = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] filePathColumn = { MediaStore.Images.Media.DATA };
            Cursor cursor = null;

            try {
                cursor = getContentResolver().query(uri, filePathColumn, null, null, null);
                int column_index = cursor.getColumnIndexOrThrow("_data");
                if (cursor.moveToFirst()) {
                    filePath = cursor.getString(column_index);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            filePath = uri.getPath();
        }
        if (filePath == null) {
            return null;
        }
        return filePath;
    }

    private static class FilesAdapter extends ArrayAdapter<EMMucShareFile> {

        List<EMMucShareFile> list;

        public FilesAdapter(@NonNull Context context, int resource, @NonNull List<EMMucShareFile> objects) {
            super(context, resource, objects);
            list = objects;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            TextView textView = new TextView(getContext());
            textView.setPadding(20,20,20,20);
            textView.setText(list.get(position).getFileName());

            return textView;
        }
    }



}