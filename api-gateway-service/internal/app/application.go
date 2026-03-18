package app

import "context"

type Application struct{}

func New(ctx context.Context) (*Application, error) {
	return nil, nil
}

func (a *Application) Run(ctx context.Context) error {
	return nil
}

func (a *Application) Stop(ctx context.Context) error {
	return nil
}
