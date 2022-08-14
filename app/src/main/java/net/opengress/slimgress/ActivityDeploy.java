package net.opengress.slimgress;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;

import java.util.Objects;

public class ActivityDeploy extends Activity {

    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final int mActionRadiusM = mGame.getKnobs().getScannerKnobs().getActionRadiusM();

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deploy);

        GameEntityPortal portal = mGame.getCurrentPortal();
    }

}
