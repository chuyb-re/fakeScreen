package top.chuyb.fakeScreen;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.WindowCompat;

/**
 * 主界面（表现层）：只负责加载 XML 布局、设置窗口外观并分发用户交互事件，
 * 状态读写与进程间通信全部由业务层 {@link FakeScreenState} 承担。
 *
 * @since 1.0
 * @author chuyb-re
 */
public class MainActivity extends AppCompatActivity {

    /**
     * 初始化界面布局、窗口元素与各控件的事件监听。
     *
     * @param savedInstanceState 保存的界面状态，可能为 null。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdgeWindow();
        setContentView(R.layout.activity_main);
        initSwitchButton();
        initHideIconButton();
        initAutoScreenOffInput();
        initGithubButton();
        initTipsButton();
    }

    /**
     * 开启 edge-to-edge 绘制，使 XML 布局延伸到系统栏下方。
     *
     * <p>系统栏颜色、状态栏图标明暗与窗口底色都在 {@code themes.xml} 中声明；
     * 只有这一个窗口开关没有对应的主题属性，因此保留在代码中。</p>
     */
    private void setupEdgeToEdgeWindow() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
    }

    /**
     * 绑定功能开关控件，读取全局设置状态，并在切换时发送广播。
     */
    private void initSwitchButton() {
        SwitchCompat switchBtn = findViewById(R.id.switch_fake_screen);
        if (switchBtn == null) return;
        switchBtn.setChecked(FakeScreenState.isEnabled(this));
        switchBtn.setOnCheckedChangeListener((buttonView, isChecked) ->
                FakeScreenState.setEnabled(this, isChecked));
    }

    /**
     * 绑定“隐藏桌面图标”开关，读取并应用启动器组件的启用状态。
     */
    private void initHideIconButton() {
        SwitchCompat switchBtn = findViewById(R.id.switch_hide_icon);
        if (switchBtn == null) return;
        switchBtn.setChecked(DesktopIconHelper.isHidden(this));
        switchBtn.setOnCheckedChangeListener((buttonView, isChecked) ->
                DesktopIconHelper.applySetting(this, isChecked));
    }

    /**
     * 绑定自动息屏时间输入框：读取当前配置，失焦或输入完成时保存。
     * 0 表示永不自动息屏。
     */
    private void initAutoScreenOffInput() {
        EditText et = findViewById(R.id.et_auto_screen_off);
        if (et == null) return;

        int current = FakeScreenState.getAutoScreenOffSeconds(this);
        et.setText(String.valueOf(current));

        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveAutoScreenOffFromInput(et);
            }
        });

        et.setOnEditorActionListener((v, actionId, event) -> {
            saveAutoScreenOffFromInput(et);
            et.clearFocus();
            return true;
        });
    }

    private void saveAutoScreenOffFromInput(EditText et) {
        String text = et.getText() != null ? et.getText().toString().trim() : "";
        if (text.isEmpty()) {
            text = "0";
            et.setText(text);
        }
        try {
            int seconds = Integer.parseInt(text);
            if (seconds < 0) {
                Toast.makeText(this, R.string.toast_invalid_timeout, Toast.LENGTH_SHORT).show();
                et.setText(String.valueOf(FakeScreenState.getAutoScreenOffSeconds(this)));
                return;
            }
            int previous = FakeScreenState.getAutoScreenOffSeconds(this);
            if (seconds != previous) {
                FakeScreenState.setAutoScreenOffSeconds(this, seconds);
                Toast.makeText(this, R.string.toast_timeout_saved, Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.toast_invalid_timeout, Toast.LENGTH_SHORT).show();
            et.setText(String.valueOf(FakeScreenState.getAutoScreenOffSeconds(this)));
        }
    }

    /**
     * 绑定 GitHub 按钮点击事件，在浏览器中打开仓库地址。
     */
    private void initGithubButton() {
        LinearLayout btnGithub = findViewById(R.id.btn_github);
        if (btnGithub == null) return;
        btnGithub.setOnClickListener(v -> {
            try {
                String url = getString(R.string.github_repo_url);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, R.string.toast_browser_unavailable, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 绑定使用提示按钮点击事件，弹出说明对话框。
     */
    private void initTipsButton() {
        LinearLayout btnTips = findViewById(R.id.btn_tips);
        if (btnTips == null) return;

        btnTips.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_tips_title)
                    .setMessage(R.string.tip)
                    .setPositiveButton(R.string.dialog_positive_btn, null)
                    .show();
        });
    }
}
