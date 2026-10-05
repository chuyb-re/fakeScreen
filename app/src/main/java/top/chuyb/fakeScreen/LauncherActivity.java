package top.chuyb.fakeScreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 占位启动页：承载桌面入口。
 *
 * <p>本页面带有 MAIN / LAUNCHER intent-filter，也就是桌面上的应用图标：{@link DesktopIconHelper}
 * 通过启用或禁用本组件来隐藏或恢复图标，从而不触碰 {@link MainActivity}，使其始终可达。
 * 点击图标会进入本页面，并立即转发到 {@link MainActivity}。</p>
 *
 * <p>它继承 {@link Activity} 而不是 {@code AppCompatActivity}：本页面不显示任何内容，也不会加载
 * 布局，这样可以继续使用半透明的无界面窗口主题。</p>
 *
 * @since 1.0
 * @author chuyb-re
 */
public class LauncherActivity extends Activity {

    /**
     * 不绘制任何内容，直接把启动请求转发到真正的主界面。
     *
     * @param savedInstanceState 保存的界面状态，可能为 null。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
