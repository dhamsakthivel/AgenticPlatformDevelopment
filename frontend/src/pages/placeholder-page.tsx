type PlaceholderPageProps = {
  title: string
  body: string
}

export function PlaceholderPage({ title, body }: PlaceholderPageProps) {
  return (
    <section className="flex max-w-2xl flex-col gap-2">
      <h1 className="text-2xl font-medium">{title}</h1>
      <p className="text-muted-foreground">{body}</p>
    </section>
  )
}
