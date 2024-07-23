/*

 Slimgress: Opengress API for Android
 Copyright (C) 2013 Norman Link <norman.link@gmx.net>
 Copyright (C) 2024 Opengress Team <info@opengress.net>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */

package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getLevelColor;
import static net.opengress.slimgress.API.Item.ItemBase.ItemType.PortalKey;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import net.opengress.slimgress.API.Common.Team;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Item.ItemPortalKey;
import net.opengress.slimgress.API.Player.Agent;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ActivityMain extends FragmentActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // update agent data
        updateAgent();
        // loading the inventory at start makes weapons, deploy etc work nicely
        // maybe should be in scanner view instead?
        mGame.intGetInventory(new Handler(msg -> {
            // Since we just updated the inventory, let's also update the status of all the portal keys
            var keys = mGame.getInventory().getItems(PortalKey);
            Set<String> portalGUIDs = new HashSet<>();
            for (var item : keys) {
                String portalGuid = ((ItemPortalKey) item).getPortalGuid();
                if (!mGame.getWorld().getGameEntities().containsKey(portalGuid)) {
                    portalGUIDs.add(portalGuid);
                }
            }
            mGame.intGetModifiedEntitiesByGuid(portalGUIDs.toArray(new String[0]), new Handler(m -> true));
            return true;
        }));

        // create ops button callback
        final Button buttonOps = findViewById(R.id.buttonOps);
        buttonOps.setOnClickListener(v -> {
            // Perform action on click
            Intent myIntent = new Intent(getApplicationContext(), ActivityOps.class);
            startActivity(myIntent);
        });

        // create comm button callback
        final Button buttonComm = findViewById(R.id.buttonComm);
        buttonComm.setOnClickListener(v -> showInfoBox("Info"));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
        Objects.requireNonNull(scanner).requestLocationUpdates();
    }

    private void showInfoBox(String message) {
        DialogInfo newDialog = new DialogInfo(this);
        newDialog.setMessage(message);
        newDialog.show();
    }

    void updateAgent() {
        // get agent data
        Agent agent = mGame.getAgent();

        // TODO move some of this style info into onCreate
        if (agent != null) {
            int textColor;
            Team team = agent.getTeam();
            textColor = 0xff000000 + team.getColour();
            var levelColor = getResources().getColor(getLevelColor(agent.getLevel()), null);

            ((TextView) findViewById(R.id.agentname)).setText(agent.getNickname());
            ((TextView) findViewById(R.id.agentname)).setTextColor(textColor);

            String agentlevel = "L" + agent.getLevel();
            ((TextView) findViewById(R.id.agentLevel)).setText(agentlevel);
            ((TextView) findViewById(R.id.agentLevel)).setTextColor(levelColor);


            String nextLevel = String.valueOf(Math.min(agent.getLevel() + 1, 8));
            ((ProgressBar) findViewById(R.id.agentap)).setMax(mGame.getKnobs().getPlayerLevelKnobs().getLevelUpRequirement(nextLevel).getApRequired());
            ((ProgressBar) findViewById(R.id.agentap)).setProgress(agent.getAp());
            ((ProgressBar) findViewById(R.id.agentap)).getProgressDrawable().setTint(levelColor);

            ((ProgressBar) findViewById(R.id.agentxm)).setMax(agent.getEnergyMax());
            ((ProgressBar) findViewById(R.id.agentxm)).setProgress(agent.getEnergy());
            ((ProgressBar) findViewById(R.id.agentxm)).getProgressDrawable().setTint(textColor);
            findViewById(R.id.agentxm).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
                    Toast.makeText(getApplicationContext(), agentinfo, Toast.LENGTH_LONG).show();
                }
            });

//            String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
//            ((TextView)findViewById(R.id.agentinfo)).setText(agentinfo);
//            ((TextView)findViewById(R.id.agentinfo)).setTextColor(0x99999999);
        }

    }
}
