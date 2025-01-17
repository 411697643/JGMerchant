package com.woniukeji.jianmerchant.partjob;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.avos.avoscloud.im.v2.AVIMClient;
import com.avos.avoscloud.im.v2.AVIMException;
import com.avos.avoscloud.im.v2.callback.AVIMClientCallback;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.woniukeji.jianmerchant.R;
import com.woniukeji.jianmerchant.base.BaseFragment;
import com.woniukeji.jianmerchant.base.Constants;
import com.woniukeji.jianmerchant.entity.BaseBean;
import com.woniukeji.jianmerchant.entity.Pigeon;
import com.woniukeji.jianmerchant.entity.PublishUser;
import com.woniukeji.jianmerchant.eventbus.FilterEvent;
import com.woniukeji.jianmerchant.http.BackgroundSubscriber;
import com.woniukeji.jianmerchant.http.HttpMethods;
import com.woniukeji.jianmerchant.http.ProgressSubscriber;
import com.woniukeji.jianmerchant.http.SubscriberOnNextListener;
import com.woniukeji.jianmerchant.utils.DateUtils;
import com.woniukeji.jianmerchant.utils.ExcelUtil;
import com.woniukeji.jianmerchant.utils.LogUtils;
import com.woniukeji.jianmerchant.utils.PopupUtils;
import com.woniukeji.jianmerchant.utils.SPUtils;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.Callback;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.leancloud.chatkit.LCChatKit;
import cn.leancloud.chatkit.activity.LCIMConversationActivity;
import cn.leancloud.chatkit.utils.LCIMConstants;
import cn.pedant.SweetAlert.SweetAlertDialog;
import de.greenrobot.event.EventBus;
import okhttp3.Call;
import okhttp3.Response;
import ui.EmptyRecyclerView;

/**
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * create an instance of this fragment.
 */
public class FilterFragment extends BaseFragment {

    private static String params1 = "type";
    private static String params2 = "jobid";
    @BindView(R.id.list)
    EmptyRecyclerView list;
    @BindView(R.id.refresh_layout) SwipeRefreshLayout refreshLayout;
    @BindView(R.id.btn_out_info) TextView btnOutInfo;
    @BindView(R.id.null_content) View mEmptyView;
    private int MSG_GET_SUCCESS = 0;
    private int MSG_GET_FAIL = 1;
    private int MSG_POST_SUCCESS = 5;
    private int MSG_POST_FAIL = 6;
    private Handler mHandler = new Myhandler(this.getActivity());
    private Context mContext = this.getActivity();
    private List<PublishUser.ListTUserInfoEntity> modleList = new ArrayList<>();
    private int lastVisibleItem;
    //    private FilterAdapter adapter;
    private NewNewFilterAdapter adapter;
    private LinearLayoutManager mLayoutManager;
    private int mPosition;
    private int type = 0;
    private String jobid;
    private String jobName;
    private boolean loadOk=false;
    private ProgressSubscriber<BaseBean> subscriber;
    private SubscriberOnNextListener<BaseBean> listenner;
    private android.view.ActionMode mActionMode;
    private int merchantId;
    private ViewGroup container;

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }

    @OnClick(R.id.btn_out_info)
    public void onClick() {
        exportUser();
    }


    private class Myhandler extends Handler {
        private WeakReference<Context> reference;

        public Myhandler(Context context) {
            reference = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    BaseBean<PublishUser> modelBaseBean = (BaseBean<PublishUser>) msg.obj;
                    int count = msg.arg1;
                    if (count == 0) {
                        modleList.clear();
                    }
                    if (refreshLayout != null && refreshLayout.isRefreshing()) {
                        refreshLayout.setRefreshing(false);
                    }
                    modleList.addAll(modelBaseBean.getData().getList_t_user_info());
                    adapter.notifyDataSetChanged();
                    loadOk=true;
                    break;
                case 1:
                    String ErrorMessage = (String) msg.obj;
                    Toast.makeText(getActivity(), ErrorMessage, Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    break;
                case 3:
                    String sms = (String) msg.obj;
                    Toast.makeText(getActivity(), sms, Toast.LENGTH_SHORT).show();
                    break;
                case 4:
                    break;
                case 5:
                    String me = (String) msg.obj;
                    Toast.makeText(getActivity(), me, Toast.LENGTH_SHORT).show();
                    FilterEvent filterEvent = new FilterEvent();
                    EventBus.getDefault().post(filterEvent);
                    break;
                case 6:
                    String mes = (String) msg.obj;
                    Toast.makeText(getActivity(), mes, Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    }
    public static FilterFragment newInstance(int type, String jobid) {
        //通过Bundle保存数据
        Bundle args = new Bundle();
        args.putInt(params1, type);
        args.putString(params2, jobid);
        FilterFragment fragment = new FilterFragment();
        //将Bundle设置为fragment的参数
        fragment.setArguments(args);
        return fragment;
    }
    public static FilterFragment newInstance(int type, String jobid,String jobname) {
        //通过Bundle保存数据
        Bundle args = new Bundle();
        args.putInt(params1, type);
        args.putString(params2, jobid);
        args.putString("jobname", jobname);
        FilterFragment fragment = new FilterFragment();
        //将Bundle设置为fragment的参数
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getArguments().getInt(params1);
        jobid = getArguments().getString(params2);
        jobName = getArguments().getString("jobname");
        merchantId = (int) SPUtils.getParam(getActivity(), "loginInfo", "id", 0);
        EventBus.getDefault().register(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.container = container;
        View view = inflater.inflate(R.layout.fragment_admit, container, false);
        ButterKnife.bind(this, view);
        initview();
        return view;

    }


    private void initview() {
        adapter = new NewNewFilterAdapter(modleList,getActivity(),type,jobid);
        adapter.setEnrollOrRefuseClickListener(new EnrollOrRefuseClickListener() {
            @Override
            public void onClick(final int position, int login_id, int type, View view) {
                String only = DateUtils.getDateTimeToOnly(System.currentTimeMillis());
                ProgressSubscriber<BaseBean> subscriber1= new ProgressSubscriber<BaseBean>(new SubscriberOnNextListener<BaseBean>() {

                    @Override
                    public void onNext(BaseBean baseBean) {
                        if (baseBean != null) {
                            modleList.remove(position);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(getHoldingContext(),baseBean.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                },getHoldingContext(),false);
                HttpMethods.getInstance().admitOrRefuseUser(subscriber1,only,jobid,String.valueOf(login_id),String.valueOf(type));
            }
        });
        adapter.setFilterItemClickListener(new FilterItemClickListener() {
            @Override
            public void onItemClick(int position) {
            }

            @Override
            public boolean onItemLongClick(final int position, View view, final int login_id) {
                //用popupwindow
                PopupUtils popupUtils = new PopupUtils(getHoldingContext());
                popupUtils.setOnSetupDove(new PopupUtils.onSetupDove() {
                    @Override
                    public void onSetup(View v) {
                        if (type != 1) {
                            Toast.makeText(getHoldingContext(), "未录用,不能标记为鸽子!", Toast.LENGTH_SHORT).show();
                        } else {
                            BackgroundSubscriber<Pigeon> subscriber = new BackgroundSubscriber<Pigeon>(new SubscriberOnNextListener<Pigeon>() {

                                @Override
                                public void onNext(Pigeon pigeon) {
                                    modleList.get(position).setPigeon_count(pigeon.getUser_info().getPigeon_count());
                                    adapter.notifyDataSetChanged();
                                }
                            },getHoldingContext());
                            HttpMethods.getInstance().markPigeon(subscriber,jobid,String.valueOf(login_id), String.valueOf(merchantId));
                        }
                    }
                });
                popupUtils.show(view);
                view.setSelected(true);
                return true;
            }
        });
        adapter.setOnChatClickListener(new onChatClickListener() {
            @Override
            public void onChat(int postion, final int login_id, View v) {
//                Toast.makeText(getHoldingContext(), "postion  "+postion+"  login_id  "+login_id, Toast.LENGTH_SHORT).show();
                LogUtils.i("onChat","position-->"+postion+"    login_id-->"+login_id);
                LCChatKit.getInstance().open(String.valueOf(merchantId), new AVIMClientCallback() {
                    @Override
                    public void done(AVIMClient avimClient, AVIMException e) {
                        if (null == e) {
                            Intent intent = new Intent(getHoldingContext(), LCIMConversationActivity.class);
                            intent.putExtra(LCIMConstants.PEER_ID, String.valueOf(login_id));
                            startActivity(intent);
                        } else {
                            Toast.makeText(getHoldingContext(), e.toString(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            }
        });
        mLayoutManager = new LinearLayoutManager(getActivity());
        list.setLayoutManager(mLayoutManager);
        list.setItemAnimator(new DefaultItemAnimator());
        list.setEmptyView(mEmptyView);
        list.setAdapter(adapter);
        refreshLayout.setColorSchemeResources(R.color.app_bg);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                GetTask getTask = new GetTask(jobid, String.valueOf(type), "0");
                getTask.execute();
            }
        });
        GetTask getTask = new GetTask(jobid, String.valueOf(type), "0");
        getTask.execute();
        //标记鸽子
        listenner = new SubscriberOnNextListener<BaseBean>() {
            @Override
            public void onNext(BaseBean baseBean) {
                Toast.makeText(getActivity(), baseBean.getMessage(), Toast.LENGTH_SHORT).show();
                FilterEvent filterEvent = new FilterEvent();
                EventBus.getDefault().post(filterEvent);
            }
        };
        subscriber = new ProgressSubscriber<>(listenner,getActivity(),false);
    }

    public void onEvent(FilterEvent filterEvent) {
        GetTask getTask = new GetTask(jobid, String.valueOf(type), "0");
        getTask.execute();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }


    @Override
    public void onStart() {
        super.onStart();
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (lastVisibleItem +1 == adapter.getItemCount()&&loadOk) {
                    loadOk=false;
                    GetTask getTask = new GetTask(jobid, String.valueOf(type), String.valueOf(lastVisibleItem));
                    getTask.execute();
                    refreshLayout.setRefreshing(true);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                lastVisibleItem = mLayoutManager.findLastVisibleItemPosition();
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    protected void visiableToUser() {

    }

    @Override
    protected void firstVisiableToUser() {

    }

    @Override
    protected void onSaveInfoToBundle(Bundle outState) {

    }

    @Override
    protected void onRestoreInfoFromBundle(Bundle savedInstanceState) {

    }

    void exportUser() {
      SweetAlertDialog sw=new SweetAlertDialog(getActivity(), SweetAlertDialog.PROGRESS_TYPE);
        sw.show();
        try {
            ExcelUtil excelUtils = new ExcelUtil(jobName, modleList);
            File file = excelUtils.create();
            ExcelUtil.openFileByOtherApp(getActivity(), file);
            if (sw.isShowing()) {
                sw.dismiss();
            }
        } catch (Exception e) {
            LogUtils.e("导出excel", e.getMessage());
            if (sw.isShowing()) {
                sw.dismiss();
            }
        }
    }

    public class GetTask extends AsyncTask<Void, Void, Void> {
        private final String jobid;
        private final String type;
        private final String count;

        GetTask(String merId, String type, String count) {
            this.jobid = merId;
            this.type = type;
            this.count = count;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                getJobs();
            } catch (Exception e) {
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        /**
         * postInfo
         */
        public void getJobs() {
            String only = DateUtils.getDateTimeToOnly(System.currentTimeMillis());
            OkHttpUtils
                    .get()
                    .url(Constants.GET_ADMIT_LIST)
                    .addParams("only", only)
                    .addParams("job_id", jobid)
                    .addParams("type", type)
                    .addParams("count", count)
                    .build()
                    .connTimeOut(60000)
                    .readTimeOut(20000)
                    .writeTimeOut(20000)
                    .execute(new Callback<BaseBean<PublishUser>>() {
                        @Override
                        public BaseBean<PublishUser> parseNetworkResponse(Response response, int id) throws Exception {
                            String string = response.body().string();
                            BaseBean baseBean = new Gson().fromJson(string, new TypeToken<BaseBean<PublishUser>>() {
                            }.getType());
                            return baseBean;
                        }

                        @Override
                        public void onError(Call call, Exception e, int id) {
                            Message message = new Message();
                            message.obj = e.toString();
                            message.what = MSG_GET_FAIL;
                            mHandler.sendMessage(message);
                        }

                        @Override
                        public void onResponse(BaseBean<PublishUser> response, int id) {
                            if (response.getCode().equals("200")) {
                                Message message = new Message();
                                message.obj = response;
                                message.arg1 = Integer.parseInt(count);
                                message.what = MSG_GET_SUCCESS;
                                mHandler.sendMessage(message);
                            } else {
                                Message message = new Message();
                                message.obj = response.getMessage();
                                message.what = MSG_GET_FAIL;
                                mHandler.sendMessage(message);
                            }
                        }



                    });
        }
    }

}
