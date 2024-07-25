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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import net.opengress.slimgress.API.Common.Team;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Player.Agent;

import java.util.Objects;

public class ActivityMain extends FragmentActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // update agent data
        updateAgent();
        mApp.getPlayerDataViewModel().getAgent().observe(this, this::updateAgent);

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

    private void updateAgent(Agent agent) {
        Log.d("ActivityMain/updateAgent", "Updating agent in display!");
// TODO move some of this style info into onCreate
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
        int thisLevelAP = mGame.getKnobs().getPlayerLevelKnobs().getLevelUpRequirement(agent.getLevel()).getApRequired();
        int nextLevelAP = mGame.getKnobs().getPlayerLevelKnobs().getLevelUpRequirement(nextLevel).getApRequired();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((ProgressBar) findViewById(R.id.agentap)).setMin(thisLevelAP);
        }
        ((ProgressBar) findViewById(R.id.agentap)).setMax(nextLevelAP);
        ((ProgressBar) findViewById(R.id.agentap)).setProgress(agent.getAp());
        ((ProgressBar) findViewById(R.id.agentap)).getProgressDrawable().setTint(levelColor);

        ((ProgressBar) findViewById(R.id.agentxm)).setMax(agent.getEnergyMax());
        ((ProgressBar) findViewById(R.id.agentxm)).setProgress(agent.getEnergy());
        ((ProgressBar) findViewById(R.id.agentxm)).getProgressDrawable().setTint(textColor);
        findViewById(R.id.activity_main_header).setOnClickListener(view -> {
            String agentinfo = "AP: " + agent.getAp() + " / " + nextLevelAP + "\nXM: " + agent.getEnergy() + " / " + agent.getEnergyMax();
            Toast.makeText(getApplicationContext(), agentinfo, Toast.LENGTH_LONG).show();
        });

//            String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
//            ((TextView)findViewById(R.id.agentinfo)).setText(agentinfo);
//            ((TextView)findViewById(R.id.agentinfo)).setTextColor(0x99999999);
    }

    void updateAgent() {
        // get agent data
        Agent agent = mGame.getAgent();

        if (agent != null) {
            updateAgent(agent);
        }

    }
}
