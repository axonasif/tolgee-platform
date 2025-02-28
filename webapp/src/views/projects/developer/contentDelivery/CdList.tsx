import { useState } from 'react';
import { Add } from '@mui/icons-material';
import { Box, Button, Typography } from '@mui/material';
import { T, useTranslate } from '@tolgee/react';

import { BoxLoading } from 'tg.component/common/BoxLoading';
import { PaginatedHateoasList } from 'tg.component/common/list/PaginatedHateoasList';
import { PaidFeatureBanner } from 'tg.ee/common/PaidFeatureBanner';
import { useGlobalContext } from 'tg.globalContext/GlobalContext';
import { useEnabledFeatures } from 'tg.globalContext/helpers';
import { useProject } from 'tg.hooks/useProject';
import { useProjectPermissions } from 'tg.hooks/useProjectPermissions';
import { useApiQuery } from 'tg.service/http/useQueryApi';

import { CdNotConfiguredAlert } from '../CdNotConfiguredAlert';
import { CdEditDialog } from './CdEditDialog';
import { CdItem } from './CdItem';

export const CdList = () => {
  const project = useProject();
  const [page, setPage] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const { t } = useTranslate();

  const serverConfiguration = useGlobalContext((c) => c.serverConfiguration);
  const contentDeliveryConfigured =
    serverConfiguration.contentDeliveryConfigured;

  const exportersLoadable = useApiQuery({
    url: '/v2/projects/{projectId}/content-delivery-configs',
    method: 'get',
    path: { projectId: project.id },
    query: { page },
  });

  const { isEnabled } = useEnabledFeatures();
  const isPaid = isEnabled('MULTIPLE_CONTENT_DELIVERY_CONFIGS');
  const { satisfiesPermission } = useProjectPermissions();

  const listSize =
    exportersLoadable.data?._embedded?.contentDeliveryConfigs?.length ?? 0;

  const listEmpty = listSize === 0;

  const canAdd =
    (isPaid || listEmpty) && satisfiesPermission('content-delivery.manage');

  if (!contentDeliveryConfigured) {
    return <CdNotConfiguredAlert />;
  }

  if (exportersLoadable.isLoading) {
    return (
      <Box mt={6}>
        <BoxLoading />
      </Box>
    );
  }

  return (
    <Box mt={4}>
      <Box
        display="flex"
        justifyContent="space-between"
        alignItems="center"
        flexWrap="wrap"
        gap={2}
        pb={1.5}
      >
        <Typography
          component="h5"
          variant="h5"
          data-cy="content-delivery-subtitle"
        >
          {t('content_delivery_subtitle')}
        </Typography>
        <Button
          variant="contained"
          color="primary"
          onClick={() => setFormOpen(true)}
          disabled={!canAdd}
          startIcon={<Add />}
          data-cy="content-delivery-add-button"
        >
          {t('content_delivery_add_button')}
        </Button>
      </Box>

      <Box pb={5}>{t('content_delivery_description')}</Box>

      <PaginatedHateoasList
        loadable={exportersLoadable}
        renderItem={(item) => <CdItem data={item} />}
        onPageChange={(val) => setPage(val)}
        emptyPlaceholder={
          <Box display="flex" justifyContent="center">
            <T keyName="global_empty_list_message" />
          </Box>
        }
      />

      {!isPaid && !listEmpty && (
        <Box mt={6}>
          <PaidFeatureBanner
            customTitle={
              listSize === 1
                ? t('content_delivery_not_enabled_title')
                : t('content_delivery_over_limit_title')
            }
            customMessage={t('content_delivery_not_enabled_message')}
          />
        </Box>
      )}
      {formOpen && <CdEditDialog onClose={() => setFormOpen(false)} />}
    </Box>
  );
};
