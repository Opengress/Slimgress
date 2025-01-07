package net.opengress.slimgress;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import net.opengress.slimgress.api.Game.GameState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FragmentScore extends Fragment {
    SlimgressApplication mApp = SlimgressApplication.getInstance();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_score, container, false);
        PieChartView pieChartView = view.findViewById(R.id.score_pie_chart);
        LinearLayout legendLayout = view.findViewById(R.id.score_legend);

        GameState game = mApp.getGame();

// FIXME teams are a lot more complicated than this in the future
        mApp.runInThread_(() -> {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> game.intGetGameScore(new Handler(msg -> {
                Map<String, Integer> teamScores = new HashMap<>();
                var enl = msg.getData().getInt("EnlightenedScore");
                var res = msg.getData().getInt("ResistanceScore");
                teamScores.put("Enlightened", enl);
                teamScores.put("Resistance", res);
                Map<String, Integer> teamColors = new HashMap<>();
                teamColors.put("Enlightened", 0xff000000 + game.getKnobs().getTeamKnobs().getTeams().get("alien").getColour());
                teamColors.put("Resistance", 0xff000000 + game.getKnobs().getTeamKnobs().getTeams().get("human").getColour());

                pieChartView.setData(teamScores, teamColors);
                updateLegend(legendLayout, teamScores, teamColors);
                return true;
            })));
        });

        return view;
    }

    private void updateLegend(LinearLayout legendLayout, Map<String, Integer> data, Map<String, Integer> colors) {
        var ctx = getContext();
        if (ctx == null) {
            return;
        }
        legendLayout.removeAllViews();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            TextView legendItem = new TextView(ctx);
            legendItem.setText(String.format(Locale.getDefault(), "%s: %d", entry.getKey(), entry.getValue()));
            legendItem.setTextColor(colors.containsKey(entry.getKey()) ? colors.get(entry.getKey()) : 0xFF888888);
            legendItem.setTextSize(16f);
            legendLayout.addView(legendItem);
        }
    }
}
