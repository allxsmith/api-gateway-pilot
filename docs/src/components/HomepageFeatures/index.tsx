import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  Svg: React.ComponentType<React.ComponentProps<'svg'>>;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Runs on your laptop',
    Svg: require('@site/static/img/feature-local.svg').default,
    description: (
      <>
        One <code>docker compose up</code> brings up Postgres, both Spring Boot
        services, and the nginx proxy. Build the whole stack before spending a
        cent on AWS.
      </>
    ),
  },
  {
    title: 'Tear down at will',
    Svg: require('@site/static/img/feature-teardown.svg').default,
    description: (
      <>
        The AWS environment is 100% Terraform. <code>terraform destroy</code>{' '}
        when you are done and <code>apply</code> when you need it back — costs
        stay near zero while idle.
      </>
    ),
  },
  {
    title: 'Documented end to end',
    Svg: require('@site/static/img/feature-docs.svg').default,
    description: (
      <>
        Every setup step, design decision, and technology swap is written down
        here, with API references and a build blog that grows alongside the
        code.
      </>
    ),
  },
];

function Feature({title, Svg, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
