import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "goodfolk. — Good food, good mood",
  description: "Comforting favourites and bright flavours, freshly made with care.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
