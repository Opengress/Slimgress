package net.opengress.slimgress.activity;

import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT;
import static net.opengress.slimgress.Constants.UNTRANSLATABLE_IMAGE_RESOLUTION_NONE;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.BulkPlayerStorage;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;

import java.util.Objects;

public class FragmentPhotoRate extends Fragment {
    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private GameEntityPortal mPortal;
    private View mRootView;

    @NonNull
    public static FragmentPhotoRate newInstance(String guid) {
        FragmentPhotoRate fragment = new FragmentPhotoRate();
        Bundle args = new Bundle();
        args.putSerializable("guid", guid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRootView = inflater.inflate(R.layout.activity_photo_rate, container, false);
        String portalGuid = getArguments().getString("guid");
        if (portalGuid != null) {
            mPortal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(portalGuid);
            if (mPortal != null) {
                setupView();
            } else {
                Log.e("PhotoRate", "Portal not found for GUID: " + portalGuid);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        } else {
            Log.e("PhotoRate", "No portal GUID provided");
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        }
        return mRootView;
    }

    private void setupView() {
        ((TextView) mRootView.findViewById(R.id.activityPhotoRateTitle)).setText(mPortal.getPortalTitle());
        String desc = mPortal.getPortalDescription();
        if (desc.isEmpty()) {
            ((TextView) mRootView.findViewById(R.id.activityPhotoRateDescription)).setText("Portal information not available");
        } else {
            ((TextView) mRootView.findViewById(R.id.activityPhotoRateDescription)).setText(mPortal.getPortalDescription());
        }

        mRootView.findViewById(R.id.activityPhotoRateOkButton).setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // FIXME everything is hardcoded
        String discoverer = "SYSTEM";
        int discovererTeamColour = 0xFF000000 + mGame.getKnobs().getTeamKnobs().fromString("system").getColour();

        if (!mPortal.getPortalAttribution().isEmpty()) {
            discoverer = mPortal.getPortalAttribution();
        } else if (mPortal.getDiscoverer() != null) {
            discoverer = mPortal.getDiscoverer().getPlain();
            discovererTeamColour = 0xFF000000 + mPortal.getDiscoverer().getTeam().getColour();
        }
        ((TextView) mRootView.findViewById(R.id.activityPhotoRateDiscoverer)).setText(discoverer);
        ((TextView) mRootView.findViewById(R.id.activityPhotoRateDiscoverer)).setTextColor(discovererTeamColour);

        BulkPlayerStorage storage = mGame.getBulkPlayerStorage();
        String desiredResolution = storage.getString(BULK_STORAGE_DEVICE_IMAGE_RESOLUTION, BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT);
        if (Objects.equals(desiredResolution, UNTRANSLATABLE_IMAGE_RESOLUTION_NONE)) {
            Glide.with(this)
                    .load(R.drawable.no_image)
                    .into((ImageView) mRootView.findViewById(R.id.activityPhotoRateImage));
        } else {
            Glide.with(this)
                    .load(mPortal.getPortalImageUrl())
                    .placeholder(R.drawable.no_image)
                    .error(R.drawable.no_image)
                    .into((ImageView) mRootView.findViewById(R.id.activityPhotoRateImage));
        }
    }
}
