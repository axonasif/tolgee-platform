import React, { ReactNode } from 'react';
import {
  Breadcrumbs,
  Link,
  Box,
  useTheme,
  useMediaQuery,
  styled,
  Typography,
} from '@mui/material';
import { NavigateNext } from '@mui/icons-material';
import { Link as RouterLink } from 'react-router-dom';

const StyledWrapper = styled('div')`
  overflow-x: auto;
  padding: 15px 0px;
  & ol {
    flex-wrap: nowrap;
  }
  & * {
    flex-shrink: 0;
  }
`;

const StyledLink = styled(Link)`
  display: grid;
  grid-auto-flow: column;
  align-items: center;
  gap: 8px;
`;

export type NavigationItem = [
  name: string | ReactNode,
  url?: string,
  icon?: ReactNode
];

type Props = {
  path: NavigationItem[];
};

export const Navigation: React.FC<Props> = ({ path }) => {
  const theme = useTheme();
  const smallScreen = useMediaQuery(theme.breakpoints.down('md'));

  return (
    <StyledWrapper>
      <Breadcrumbs
        aria-label="breadcrumb"
        separator={<NavigateNext fontSize="small" />}
        itemsBeforeCollapse={0}
        maxItems={smallScreen ? 1 : undefined}
      >
        {path.map(([name, url, icon], index) => {
          const color =
            index === path.length - 1 ? theme.palette.primaryText : undefined;
          if (React.isValidElement(name)) {
            return (
              <Box data-cy="navigation-item" sx={{ color }} key={index}>
                {name}
              </Box>
            );
          } else if (url) {
            return (
              <StyledLink
                data-cy="navigation-item"
                key={index}
                sx={{ color }}
                // @ts-ignore
                to={url}
                component={RouterLink}
              >
                {icon}
                {name}
              </StyledLink>
            );
          } else {
            return (
              <Typography data-cy="navigation-item" key={index} sx={{ color }}>
                {icon}
                {name}
              </Typography>
            );
          }
        })}
      </Breadcrumbs>
    </StyledWrapper>
  );
};
