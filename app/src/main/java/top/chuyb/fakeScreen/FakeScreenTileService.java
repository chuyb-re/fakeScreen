package top.chuyb.fakeScreen;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * 快捷设置磁贴，作用与界面上的功能开关一致。
 *
 * <p>磁贴与开关共用同一业务层（{@link FakeScreenState}），因此读写的是同一份状态。</p>
 *
 * @since 1.0
 * @author chuyb-re
 */
public class FakeScreenTileService extends TileService {

    /**
     * 切换功能开关，并立即刷新磁贴，不等待 system_server 完成状态持久化。
     */
    @Override
    public void onClick() {
        boolean enabled = !FakeScreenState.isEnabled(this);
        FakeScreenState.setEnabled(this, enabled);
        applyTileState(enabled);
    }

    /**
     * 磁贴被添加时回调。此时磁贴对象通常尚未绑定，因此真正的刷新在
     * {@link #onStartListening()} 中进行，这里只是尽力尝试。
     */
    @Override
    public void onTileAdded() {
        checkEnable();
    }

    /**
     * 磁贴变为可见时回调，这才是可靠地刷新磁贴状态的时机。
     */
    @Override
    public void onStartListening() {
        super.onStartListening();
        checkEnable();
    }

    /**
     * 读取持久化的状态并应用到磁贴。
     */
    private void checkEnable() {
        applyTileState(FakeScreenState.isEnabled(this));
    }

    /**
     * 把给定状态应用到磁贴。
     *
     * <p>{@link #getQsTile()} 在服务未处于监听状态时返回 null，因此每次调用都重新获取，
     * 不缓存在字段中，并做空判断。</p>
     *
     * @param enabled 当前功能状态。
     */
    private void applyTileState(boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
