import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'API Gateway Pilot',
  tagline: 'A prototype API gateway architecture on AWS',
  favicon: 'img/favicon.svg',

  // Production URL — GitHub Pages for the repo.
  url: 'https://allxsmith.github.io',
  baseUrl: '/api-gateway-pilot/',

  organizationName: 'allxsmith',
  projectName: 'api-gateway-pilot',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  markdown: {
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },

  themes: ['@docusaurus/theme-mermaid', 'docusaurus-theme-openapi-docs'],

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl:
            'https://github.com/allxsmith/api-gateway-pilot/tree/main/docs/',
          // Required by docusaurus-theme-openapi-docs to render API pages.
          docItemComponent: '@theme/ApiItem',
        },
        blog: {
          showReadingTime: true,
          blogTitle: 'Engineering blog',
          blogDescription: 'Build notes from the API Gateway Pilot prototype',
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          editUrl:
            'https://github.com/allxsmith/api-gateway-pilot/tree/main/docs/',
          onInlineTags: 'warn',
          onInlineAuthors: 'warn',
          onUntruncatedBlogPosts: 'warn',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  plugins: [
    '@signalwire/docusaurus-plugin-llms-txt',
    'docusaurus-plugin-sass',
    [
      'docusaurus-plugin-openapi-docs',
      {
        id: 'openapi',
        docsPluginId: 'classic',
        config: {
          'auth-server': {
            specPath: 'openapi/auth-server.yaml',
            outputDir: 'docs/api/auth-server',
            sidebarOptions: {
              groupPathsBy: 'tag',
            },
          },
          'resource-api': {
            specPath: 'openapi/resource-api.yaml',
            outputDir: 'docs/api/resource-api',
            sidebarOptions: {
              groupPathsBy: 'tag',
            },
          },
        },
      },
    ],
  ],

  themeConfig: {
    image: 'img/social-card.svg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'API Gateway Pilot',
      logo: {
        alt: 'API Gateway Pilot logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Docs',
        },
        {
          label: 'API Reference',
          position: 'left',
          items: [
            {
              type: 'docSidebar',
              sidebarId: 'authServerSidebar',
              label: 'auth-server',
            },
            {
              type: 'docSidebar',
              sidebarId: 'resourceApiSidebar',
              label: 'resource-api',
            },
          ],
        },
        {to: '/blog', label: 'Blog', position: 'left'},
        {
          href: 'https://github.com/allxsmith/api-gateway-pilot',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Overview', to: '/docs/intro'},
            {label: 'Architecture', to: '/docs/architecture/overview'},
            {label: 'Local development', to: '/docs/local-development'},
          ],
        },
        {
          title: 'API Reference',
          items: [
            {label: 'auth-server', to: '/docs/api/auth-server/auth-server'},
            {label: 'resource-api', to: '/docs/api/resource-api/resource-api'},
          ],
        },
        {
          title: 'More',
          items: [
            {label: 'Blog', to: '/blog'},
            {
              label: 'GitHub',
              href: 'https://github.com/allxsmith/api-gateway-pilot',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} API Gateway Pilot. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'hcl', 'sql', 'bash', 'properties'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
