import { useTranslate } from '@tolgee/react';
import { exhaustiveMatchingGuard } from 'tg.fixtures/exhaustiveMatchingGuard';
import { components } from 'tg.service/apiSchema.generated';

type Feature =
  components['schemas']['SelfHostedEePlanModel']['enabledFeatures'][number];

export function useFeatureTranslation() {
  const { t } = useTranslate();

  return (value: Feature) => {
    switch (value) {
      case 'GRANULAR_PERMISSIONS':
        return t('billing_subscriptions_granular_permissions_feature');
      case 'PREMIUM_SUPPORT':
        return t('billing_subscriptions_premium_support_feature');
      case 'BACKUP_CONFIGURATION':
        return t('billing_subscriptions_backup_configuration_feature');
      case 'ACCOUNT_MANAGER':
        return t('billing_subscriptions_account_manager_feature');
      case 'ASSISTED_UPDATES':
        return t('billing_subscriptions_assisted_updates_feature');
      case 'DEDICATED_SLACK_CHANNEL':
        return t('billing_subscriptions_dedicated_slack_channel');
      case 'DEPLOYMENT_ASSISTANCE':
        return t('billing_subscriptions_deployment_assistance');
      case 'PRIORITIZED_FEATURE_REQUESTS':
        return t('billing_subscriptions_prioritized_feature_requests');
      case 'TEAM_TRAINING':
        return t('billing_subscriptions_team_training');
      case 'STANDARD_SUPPORT':
        return t('billing_subscriptions_standard_support');
      case 'PROJECT_LEVEL_CONTENT_STORAGES':
        return t('billing_subscriptions_project_level_content_storages');
      case 'MULTIPLE_CONTENT_DELIVERY_CONFIGS':
        return t('billing_subscriptions_multiple_content_delivery_configs');
      case 'WEBHOOKS':
        return t('billing_subscriptions_webhooks');
      default:
        exhaustiveMatchingGuard(value);
        return value;
    }
  };
}
