package net.opengress.slimgress.activity;

import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT;
import static net.opengress.slimgress.Constants.UNTRANSLATABLE_IMAGE_RESOLUTION_NONE;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.BulkPlayerStorage;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;

import java.util.Objects;

public class ActivityPhotoRate extends AppCompatActivity {
    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private GameEntityPortal mPortal;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_rate);
        String portalGuid = getIntent().getStringExtra("guid");
        if (portalGuid != null) {
            mPortal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(portalGuid);
            if (mPortal != null) {
                setupView();
            } else {
                Log.e("ActivityPortal", "Portal not found for GUID: " + portalGuid);
                finish();
            }
        } else {
            Log.e("ActivityPortal", "No portal GUID provided");
            finish();
        }

    }

    private void setupView() {
        ((TextView) findViewById(R.id.activityPhotoRateTitle)).setText(mPortal.getPortalTitle());
        String desc = mPortal.getPortalDescription();
        if (desc.isEmpty()) {
            ((TextView) findViewById(R.id.activityPhotoRateDescription)).setText("Portal information not available");
        } else {
            ((TextView) findViewById(R.id.activityPhotoRateDescription)).setText(mPortal.getPortalDescription());
        }

        // FIXME everything is hardcoded
        String discoverer = "SYSTEM";
        int discovererTeamColour = 0xFF000000 + mGame.getKnobs().getTeamKnobs().fromString("system").getColour();

        if (!mPortal.getPortalAttribution().isEmpty()) {
            discoverer = mPortal.getPortalAttribution();
        } else if (mPortal.getDiscoverer() != null) {
            discoverer = mPortal.getDiscoverer().getPlain();
            discovererTeamColour = 0xFF000000 + mPortal.getDiscoverer().getTeam().getColour();
        }
        ((TextView) findViewById(R.id.activityPhotoRateDiscoverer)).setText(discoverer);
        ((TextView) findViewById(R.id.activityPhotoRateDiscoverer)).setTextColor(discovererTeamColour);

        BulkPlayerStorage storage = mGame.getBulkPlayerStorage();
        String desiredResolution = storage.getString(BULK_STORAGE_DEVICE_IMAGE_RESOLUTION, BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT);
        if (Objects.equals(desiredResolution, UNTRANSLATABLE_IMAGE_RESOLUTION_NONE)) {
            Glide.with(this)
                    .load(R.drawable.no_image)
                    .into((ImageView) findViewById(R.id.activityPhotoRateImage));
        } else {
            Glide.with(this)
                    .load(mPortal.getPortalImageUrl())
                    .placeholder(R.drawable.no_image)
                    .error(R.drawable.no_image)
                    .into((ImageView) findViewById(R.id.activityPhotoRateImage));
        }
    }
}
