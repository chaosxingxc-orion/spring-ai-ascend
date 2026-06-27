import { useState, type MouseEvent } from 'react';
import { toggleDiscoverFavorite } from '../api/discover';

interface DiscoverFavoriteButtonProps {
  type: string;
  id: string;
  favorite?: boolean;
  compact?: boolean;
  onChanged?: (favorite: boolean) => void;
}

export function DiscoverFavoriteButton({
  type,
  id,
  favorite = false,
  compact = false,
  onChanged,
}: DiscoverFavoriteButtonProps) {
  const [busy, setBusy] = useState(false);
  const [isFavorite, setIsFavorite] = useState(favorite);

  const handleToggle = async (event: MouseEvent) => {
    event.stopPropagation();
    event.preventDefault();
    setBusy(true);
    try {
      const next = !isFavorite;
      await toggleDiscoverFavorite(type, id, next);
      setIsFavorite(next);
      onChanged?.(next);
    } finally {
      setBusy(false);
    }
  };

  return (
    <button
      type="button"
      className={`btn ghost${compact ? ' compact' : ''} discover-favorite-btn${isFavorite ? ' active' : ''}`}
      disabled={busy}
      title={isFavorite ? '取消收藏' : '加入收藏'}
      aria-pressed={isFavorite}
      onClick={(event) => void handleToggle(event)}
    >
      {busy ? '…' : isFavorite ? '★' : '☆'}
    </button>
  );
}
