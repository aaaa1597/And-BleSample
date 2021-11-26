package com.test.blesample.central;

import android.app.Activity;
import android.support.v4.content.res.ResourcesCompat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.TextView;

public class MsgPopUp extends PopupWindow {
	/* コンストラクタ */
	private MsgPopUp(Activity activity) {
		super(activity);
	}

	/* windows生成 */
	public static MsgPopUp create(Activity activity) {
		MsgPopUp retwindow = new MsgPopUp(activity);
		View popupView = activity.getLayoutInflater().inflate(R.layout.popup_layout, null);
		popupView.findViewById(R.id.btnClose).setOnClickListener(v -> {
			android.os.Process.killProcess(android.os.Process.myPid());
		});
		retwindow.setContentView(popupView);
		/* 背景設定 */
		retwindow.setBackgroundDrawable(ResourcesCompat.getDrawable(activity.getResources(), R.drawable.popup_background, null));

		/* タップ時に他のViewでキャッチされないための設定 */
		retwindow.setOutsideTouchable(true);
		retwindow.setFocusable(true);

		/* 表示サイズの設定 今回は幅300dp */
		float width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300, activity.getResources().getDisplayMetrics());
		retwindow.setWidth((int)width);
		retwindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
		return retwindow;
	}

	/* 文字列設定 */
	public MsgPopUp setErrMsg(String errmsg) {
		((TextView)this.getContentView().findViewById(R.id.txtErrMsg)).setText(errmsg);
		return this;
	}

	/* 表示 */
	public void Show(Activity activity) {
		View anchor = ((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
		this.showAtLocation(anchor, Gravity.CENTER,0, 0);
	}
}
