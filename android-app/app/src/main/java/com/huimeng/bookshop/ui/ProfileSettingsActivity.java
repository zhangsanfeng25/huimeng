package com.huimeng.bookshop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.huimeng.bookshop.core.SessionStore;
import com.huimeng.bookshop.databinding.ActivityProfileSettingsBinding;

public final class ProfileSettingsActivity extends AppCompatActivity {
    private ActivityProfileSettingsBinding binding;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        binding = ActivityProfileSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.back.setOnClickListener(v -> finish());
        binding.editAccount.setOnClickListener(v -> openAccountPage(EditAccountActivity.class));
        binding.appearance.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        binding.feedback.setOnClickListener(v -> ServiceEntries.account(this, () -> ServiceActivity.open(this, "feedback")));
        binding.merchantApplication.setOnClickListener(v -> openAccountPage(MerchantApplicationActivity.class));
        binding.logout.setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        boolean signedIn = new SessionStore(this).authenticated();
        binding.logout.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        binding.editAccount.setText(signedIn ? "编辑账户" : "登录账户");
    }

    private void openAccountPage(Class<?> page) {
        ServiceEntries.account(this, () -> startActivity(new Intent(this, page)));
    }
}
